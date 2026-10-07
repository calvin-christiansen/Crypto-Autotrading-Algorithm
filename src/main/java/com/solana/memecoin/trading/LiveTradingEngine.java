package com.solana.memecoin.trading;

import com.solana.memecoin.api.DexScreenerClient;
import com.solana.memecoin.api.JupiterClient;
import com.solana.memecoin.config.BotConfig;
import com.solana.memecoin.model.Position;
import com.solana.memecoin.model.TokenInfo;
import com.solana.memecoin.model.Trade;
import com.solana.memecoin.wallet.SolanaWalletManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live trading engine using DexScreener for discovery and Jupiter for execution
 */
public class LiveTradingEngine {
    private static final Logger logger = LoggerFactory.getLogger(LiveTradingEngine.class);
    
    private final BotConfig config;
    private final DexScreenerClient dexScreenerClient;
    private final JupiterClient jupiterClient;
    private final SolanaWalletManager walletManager;
    
    private final Map<String, Position> activePositions;
    private final List<Trade> tradeHistory;
    private double sessionPnL; // Total P&L for this trading session (starts at $0)
    private double sessionFeesUsd; // Total fees paid this session
    private double dailyLoss;
    private Instant lastDayReset;
    private Instant sessionStartTime;
    private MarketCondition currentMarketCondition;
    
    // Market condition detection
    private enum MarketCondition {
        BULL,      // Strong uptrend - be more aggressive
        SIDEWAYS,  // Choppy/neutral - normal strategy
        BEAR       // Downtrend - be more conservative
    }
    
    private volatile boolean running;
    private int lastScanTokenCount = 0;
    private final Map<String, Instant> recentlySoldTokens;
    private final Map<String, Integer> positionErrorCounts; // Track consecutive errors per position
    private static final int COOLDOWN_MINUTES = 30;
    private static final int MAX_POSITION_ERRORS = 5; // Max consecutive errors before force-closing
    
    public LiveTradingEngine(BotConfig config) {
        this.config = config;
        this.dexScreenerClient = new DexScreenerClient(config);
        this.walletManager = new SolanaWalletManager(config);
        this.jupiterClient = new JupiterClient(config, walletManager);
        
        this.activePositions = new ConcurrentHashMap<>();
        this.tradeHistory = new ArrayList<>();
        this.recentlySoldTokens = new ConcurrentHashMap<>();
        this.positionErrorCounts = new ConcurrentHashMap<>();
        this.sessionPnL = 0.0; // Session P&L starts at $0
        this.sessionFeesUsd = 0.0;
        this.dailyLoss = 0.0;
        this.lastDayReset = Instant.now();
        this.sessionStartTime = Instant.now();
        this.running = false;
        this.currentMarketCondition = MarketCondition.SIDEWAYS; // Start neutral
        
        logger.info("Live Trading Engine initialized");
    }
    
    /**
     * Start the live trading loop
     */
    public void start() {
        if (running) {
            logger.warn("Trading engine is already running");
            return;
        }
        
        running = true;
        logger.info("════════════════════════════════════════════════");
        logger.info("         LIVE TRADING ENGINE STARTED");
        logger.info("════════════════════════════════════════════════");
        
        // Check wallet balance (skip in dry-run mode)
        if (config.dryRunMode) {
            logger.info("Trading Mode: DRY RUN (wallet balance check skipped)");
        } else {
            double solBalance = walletManager.getSolBalance();
            logger.info("Wallet SOL Balance: {} SOL", String.format("%.4f", solBalance));
            
            if (solBalance < 0.01) {
                logger.error("Insufficient SOL balance for trading. Please add funds to your wallet.");
                running = false;
                return;
            }
            logger.info("Trading Mode: LIVE");
        }
        logger.info("Max Concurrent Positions: {}", config.maxConcurrentPositions);
        logger.info("Max Position Size: ${}", config.maxPositionSizeUsd);
        logger.info("Stop Loss: {}%", config.stopLossPercent);
        logger.info("Take Profit: {}%", config.takeProfitPercent);
        logger.info("════════════════════════════════════════════════\n");
        
        // Main trading loop
        while (running) {
            try {
                // Reset daily loss counter
                checkDailyReset();
                
                // Detect current market conditions
                detectMarketConditions();
                
                // Monitor existing positions
                monitorPositions();
                
                // Look for new opportunities if we have capacity
                if (activePositions.size() < config.maxConcurrentPositions) {
                    scanForOpportunities();
                }
                
                // Sleep before next scan
                Thread.sleep(config.scanIntervalSeconds * 1000L);
                
            } catch (InterruptedException e) {
                logger.info("Trading engine interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in trading loop", e);
            }
        }
        
        logger.info("Trading engine stopped");
    }
    
    /**
     * Stop the trading engine
     */
    public void stop() {
        logger.info("\n╔═══════════════════════════════════════════════╗");
        logger.info("║         STOPPING TRADING ENGINE              ║");
        logger.info("╚═══════════════════════════════════════════════╝");
        running = false;
        
        // Close all open positions
        if (!activePositions.isEmpty()) {
            logger.info("\nClosing {} open position(s)...", activePositions.size());
            for (Position position : activePositions.values()) {
                logger.info("  → Closing position: {}", position.getSymbol());
                closePosition(position, Trade.TradeReason.MANUAL_EXIT);
            }
        }
        
        // Display final session summary
        displaySessionSummary();
    }
    
    /**
     * Scan for new trading opportunities - scans 500 tokens
     */
    private void scanForOpportunities() {
        try {
            // Get 500 trending tokens from DexScreener
            List<TokenInfo> tokens = dexScreenerClient.getTrendingTokens();
            lastScanTokenCount = tokens.size();
            
            if (tokens.isEmpty()) {
                return;
            }
            
            // Get adjusted entry threshold based on market conditions
            double adjustedThreshold = getAdjustedEntryThreshold();
            
            // Score and filter tokens
            List<TokenInfo> candidates = new ArrayList<>();
            for (TokenInfo token : tokens) {
                double score = calculateTokenScore(token);
                token.setScore(score);
                
                if (score >= adjustedThreshold && meetsRequirements(token)) {
                    candidates.add(token);
                }
            }
            
            // Sort by score
            candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            
            if (!candidates.isEmpty()) {
                // Add randomization: select from top 20% of candidates instead of always top 3
                // This ensures variety while still favoring high-scoring tokens
                int topCandidatesCount = Math.max(3, (int) (candidates.size() * 0.2));
                List<TokenInfo> topCandidates = candidates.subList(0, Math.min(topCandidatesCount, candidates.size()));
                
                // Shuffle the top candidates for randomization
                java.util.Collections.shuffle(topCandidates);
                
                // Take up to 3 positions from the shuffled top candidates
                int positionsToOpen = Math.min(
                    topCandidates.size(),
                    config.maxConcurrentPositions - activePositions.size()
                );
                
                for (int i = 0; i < Math.min(positionsToOpen, 3); i++) {
                    TokenInfo bestToken = topCandidates.get(i);
                    
                    // Open position
                    openPosition(bestToken);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error scanning for opportunities", e);
        }
    }
    
    /**
     * Monitor existing positions for exit signals
     */
    private void monitorPositions() {
        if (activePositions.isEmpty()) {
            return;
        }
        
        for (Position position : new ArrayList<>(activePositions.values())) {
            String tokenAddress = position.getTokenAddress();
            
            try {
                // Get current price from DexScreener (more reliable than Jupiter)
                TokenInfo tokenInfo = dexScreenerClient.getTokenInfo(tokenAddress);
                
                if (tokenInfo == null || tokenInfo.getPriceUsd() <= 0) {
                    // Increment error counter
                    int errorCount = positionErrorCounts.getOrDefault(tokenAddress, 0) + 1;
                    positionErrorCounts.put(tokenAddress, errorCount);
                    
                    // If too many consecutive errors, force close position to prevent stuck positions
                    if (errorCount >= MAX_POSITION_ERRORS) {
                        logger.warn("Position {} has {} consecutive errors, force closing", 
                            position.getSymbol(), errorCount);
                        activePositions.remove(tokenAddress);
                        positionErrorCounts.remove(tokenAddress);
                    }
                    continue;
                }
                
                // Reset error counter on successful price fetch
                positionErrorCounts.remove(tokenAddress);
                
                double currentPrice = tokenInfo.getPriceUsd();
                position.updatePrice(currentPrice);
                double pnlPercent = position.getPnLPercent();
                
                // Get dynamic take profit based on time held
                double dynamicTakeProfit = position.getDynamicTakeProfitPercent();
                
                // PROFITABILITY IMPROVEMENT: Fee-aware exit strategy
                // Calculate total fees (2 transactions: buy + sell)
                double totalFeesPercent = (calculateTransactionFees(position.getPositionSizeUsd()) * 2.0 / position.getPositionSizeUsd()) * 100;
                double minProfitTarget = totalFeesPercent + 3.0; // Cover fees + 3% minimum profit
                
                // Stop loss: Quick exit on losers (tighter than before)
                if (pnlPercent <= -10.0) {
                    closePosition(position, Trade.TradeReason.STOP_LOSS);
                    positionErrorCounts.remove(tokenAddress);
                    
                } else if (dynamicTakeProfit == 0.0) {
                    // Position held for 60 minutes - force sell regardless of profit
                    closePosition(position, Trade.TradeReason.TAKE_PROFIT);
                    positionErrorCounts.remove(tokenAddress);
                    
                } else if (pnlPercent >= dynamicTakeProfit) {
                    // Hit dynamic take profit target  
                    closePosition(position, Trade.TradeReason.TAKE_PROFIT);
                    positionErrorCounts.remove(tokenAddress);
                    
                } else if (pnlPercent >= 25.0) {
                    // IMPROVED: Take profits at 25% to lock in wins (covers fees + good profit)
                    closePosition(position, Trade.TradeReason.TAKE_PROFIT);
                    positionErrorCounts.remove(tokenAddress);
                    
                } else if (pnlPercent >= minProfitTarget) {
                    // PROFITABILITY: Exit once we cover fees + minimum profit (usually ~8-10%)
                    Duration holdTime = Duration.between(position.getOpenedAt(), Instant.now());
                    if (holdTime.toMinutes() >= 15) {
                        // Only take early profits if held for at least 15 minutes
                        closePosition(position, Trade.TradeReason.TAKE_PROFIT);
                        positionErrorCounts.remove(tokenAddress);
                    }
                    
                } else {
                    // IMPROVED: Tighter trailing stop at 6% to protect profits
                    if (position.getHighestPrice() > 0) {
                        double dropFromHigh = ((position.getHighestPrice() - currentPrice) / 
                                position.getHighestPrice()) * 100;
                        
                        if (dropFromHigh >= 6.0 && pnlPercent > minProfitTarget) {
                            // Only trigger if we're above minimum profit target
                            closePosition(position, Trade.TradeReason.TRAILING_STOP);
                            positionErrorCounts.remove(tokenAddress);
                        }
                    }
                }
                
            } catch (Exception e) {
                // Increment error counter
                int errorCount = positionErrorCounts.getOrDefault(tokenAddress, 0) + 1;
                positionErrorCounts.put(tokenAddress, errorCount);
                
                logger.error("Error monitoring position {} (error #{}/{}): {}", 
                    position.getSymbol(), errorCount, MAX_POSITION_ERRORS, e.getMessage());
                
                // Force close if too many errors
                if (errorCount >= MAX_POSITION_ERRORS) {
                    logger.warn("Force closing position {} due to persistent errors", position.getSymbol());
                    try {
                        closePosition(position, Trade.TradeReason.MANUAL_EXIT);
                    } catch (Exception closeError) {
                        logger.error("Failed to close problematic position {}", position.getSymbol());
                        // Remove from active positions to prevent infinite errors
                        activePositions.remove(tokenAddress);
                    }
                    positionErrorCounts.remove(tokenAddress);
                }
            }
        }
    }
    
    /**
     * Open a new position
     */
    private void openPosition(TokenInfo token) {
        if (activePositions.containsKey(token.getAddress())) {
            return;
        }
        
        // Check if this token was recently sold (cooldown period)
        if (recentlySoldTokens.containsKey(token.getAddress())) {
            Instant soldTime = recentlySoldTokens.get(token.getAddress());
            Duration timeSinceSold = Duration.between(soldTime, Instant.now());
            if (timeSinceSold.toMinutes() < COOLDOWN_MINUTES) {
                logger.debug("Skipping {} - sold {} minutes ago (cooldown: {} min)", 
                    token.getSymbol(), timeSinceSold.toMinutes(), COOLDOWN_MINUTES);
                return;
            } else {
                // Cooldown expired, remove from map
                recentlySoldTokens.remove(token.getAddress());
            }
        }
        
        if (activePositions.size() >= config.maxConcurrentPositions) {
            logger.warn("⚠️  Max positions reached ({})", config.maxConcurrentPositions);
            return;
        }
        
        if (dailyLoss >= config.maxDailyLossUsd) {
            logger.warn("⚠️  Daily loss limit reached: ${}", String.format("%.2f", dailyLoss));
            return;
        }
        
        // Calculate smart position size based on volatility and market conditions
        double positionSize = calculateSmartPositionSize(token);
        
        // Calculate dynamic slippage based on liquidity
        int slippageBps = calculateDynamicSlippage(token);
        
        // Execute buy via Jupiter with dynamic slippage
        String txSignature = jupiterClient.executeBuy(token.getAddress(), positionSize, slippageBps);
        
        if (txSignature != null) {
            Position position = new Position(
                    token.getAddress(),
                    token.getSymbol(),
                    token.getPriceUsd(),
                    positionSize / token.getPriceUsd(),
                    positionSize
            );
            
            activePositions.put(token.getAddress(), position);
            
            Trade trade = new Trade(
                    token.getAddress(),
                    token.getSymbol(),
                    Trade.TradeType.BUY,
                    token.getPriceUsd(),
                    positionSize / token.getPriceUsd(),
                    positionSize,
                    Trade.TradeReason.SIGNAL,
                    txSignature
            );
            
            // Calculate and track transaction fees
            double estimatedFee = calculateTransactionFees(positionSize);
            trade.setTransactionFeeUsd(estimatedFee);
            sessionFeesUsd += estimatedFee;
            
            tradeHistory.add(trade);
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logger.info("✅ BUY {} | Price: ${} | Amount: {} | Value: ${} | Slippage: {}% | Vol: ${} | Liq: ${} | Market: {} | Time: {}", 
                    token.getSymbol(),
                    String.format("%.6f", token.getPriceUsd()),
                    String.format("%.2f", positionSize / token.getPriceUsd()),
                    String.format("%.2f", positionSize),
                    String.format("%.2f", slippageBps / 100.0),
                    String.format("%.0f", token.getVolume24h()),
                    String.format("%.0f", token.getLiquidityUsd()),
                    currentMarketCondition,
                    timestamp);
        } else {
            logger.error("❌ Failed to buy {}", token.getSymbol());
        }
    }
    
    /**
     * Close an existing position
     */
    private void closePosition(Position position, Trade.TradeReason reason) {
        // Get current token info for dynamic slippage
        TokenInfo tokenInfo = null;
        try {
            tokenInfo = dexScreenerClient.getTokenInfo(position.getTokenAddress());
        } catch (Exception e) {
            logger.warn("Could not fetch token info for slippage calculation, using default");
        }
        
        // Calculate dynamic slippage (or use default if token info unavailable)
        int slippageBps = tokenInfo != null ? calculateDynamicSlippage(tokenInfo) : 100;
        
        // Execute sell via Jupiter (assuming 9 decimals for most tokens) with dynamic slippage
        String txSignature = jupiterClient.executeSell(
                position.getTokenAddress(),
                position.getTokenAmount(),
                9,
                slippageBps
        );
        
        if (txSignature != null) {
            double exitValue = position.getTokenAmount() * position.getCurrentPrice();
            double pnl = exitValue - position.getPositionSizeUsd();
            
            if (pnl < 0) {
                dailyLoss += Math.abs(pnl);
            }
            sessionPnL += pnl;
            
            Trade trade = new Trade(
                    position.getTokenAddress(),
                    position.getSymbol(),
                    Trade.TradeType.SELL,
                    position.getCurrentPrice(),
                    position.getTokenAmount(),
                    exitValue,
                    reason,
                    txSignature
            );
            
            // Calculate and track transaction fees
            double estimatedFee = calculateTransactionFees(exitValue);
            trade.setTransactionFeeUsd(estimatedFee);
            trade.setPnL(pnl);
            sessionFeesUsd += estimatedFee;
            
            tradeHistory.add(trade);
            
            activePositions.remove(position.getTokenAddress());
            
            // Add to cooldown map to prevent immediate re-buy
            recentlySoldTokens.put(position.getTokenAddress(), Instant.now());
            
            String pnlColor = pnl >= 0 ? "+" : "";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logger.info("💰 SELL {} | Reason: {} | Entry: ${} | Exit: ${} | PnL: {}${} ({}{}%) | Session P&L: {}${} | Time: {}", 
                    position.getSymbol(),
                    reason,
                    String.format("%.6f", position.getEntryPrice()),
                    String.format("%.6f", position.getCurrentPrice()),
                    pnlColor,
                    String.format("%.2f", pnl),
                    pnlColor,
                    String.format("%.1f", position.getPnLPercent()),
                    sessionPnL >= 0 ? "+" : "",
                    String.format("%.2f", sessionPnL),
                    timestamp);
        } else {
            logger.error("❌ Failed to sell {}", position.getSymbol());
        }
    }
    
    /**
     * Calculate score for a token
     */
    private double calculateTokenScore(TokenInfo token) {
        double momentumScore = calculateMomentumScore(token);
        double volumeScore = calculateVolumeScore(token);
        double liquidityScore = calculateLiquidityScore(token);
        double sentimentScore = 50.0; // Placeholder since we don't have sentiment API
        double safetyScore = calculateSafetyScore(token);
        
        double baseScore = (momentumScore * config.weightMomentum) +
               (volumeScore * config.weightVolume) +
               (liquidityScore * config.weightLiquidity) +
               (sentimentScore * config.weightSentiment) +
               (safetyScore * config.weightSafety);
        
        // PROFITABILITY IMPROVEMENT: Penalize tokens with poor liquidity-to-volume ratio
        // High volume with low liquidity = high slippage = harder to profit
        if (token.getVolume24h() > 0 && token.getLiquidityUsd() > 0) {
            double liquidityRatio = token.getLiquidityUsd() / token.getVolume24h();
            if (liquidityRatio < 0.1) {
                baseScore *= 0.8; // 20% penalty for potential high slippage
            }
        }
        
        return baseScore;
    }
    
    private double calculateMomentumScore(TokenInfo token) {
        double priceChange24h = token.getPriceChange24h();
        // PROFITABILITY IMPROVEMENT: Favor strong momentum but not crazy pumps
        // Tokens that pump 100%+ often dump immediately
        if (priceChange24h > 200) return 50; // Too volatile, likely dump incoming
        if (priceChange24h > 100) return 70; // High risk/reward
        if (priceChange24h > 50) return 90;  // Sweet spot - strong momentum
        if (priceChange24h > 30) return 85;  // Good momentum
        if (priceChange24h > 20) return 75;
        if (priceChange24h > 10) return 55;
        return 30; // Weak momentum
    }
    
    private double calculateVolumeScore(TokenInfo token) {
        double volume = token.getVolume24h();
        // PROFITABILITY IMPROVEMENT: Higher minimum volume requirement
        if (volume > 2_000_000) return 100;
        if (volume > 1_000_000) return 90;
        if (volume > 500_000) return 75;
        if (volume > 200_000) return 60;
        if (volume > 100_000) return 45;
        return 20; // Low volume = low score
    }
    
    private double calculateLiquidityScore(TokenInfo token) {
        double liquidity = token.getLiquidityUsd();
        // PROFITABILITY IMPROVEMENT: Require higher liquidity for better execution
        if (liquidity > 200_000) return 100;
        if (liquidity > 100_000) return 90;
        if (liquidity > 50_000) return 75;
        if (liquidity > 30_000) return 60;
        if (liquidity > 20_000) return 45;
        return 20; // Low liquidity = high slippage
    }
    
    private double calculateSafetyScore(TokenInfo token) {
        double score = 50.0;
        
        // Age check
        if (token.getCreatedAt() != null) {
            Duration age = Duration.between(token.getCreatedAt(), Instant.now());
            long ageHours = age.toHours();
            
            if (ageHours < config.maxTokenAgeHours) {
                score += 25;
            }
        }
        
        // Liquidity to market cap ratio
        if (token.getMarketCapUsd() > 0) {
            double ratio = token.getLiquidityUsd() / token.getMarketCapUsd();
            if (ratio > 0.1) score += 25;
        }
        
        return Math.min(score, 100);
    }
    
    private boolean meetsRequirements(TokenInfo token) {
        // PROFITABILITY IMPROVEMENT: Stricter requirements to filter out low-quality tokens
        boolean meetsVolume = token.getVolume24h() >= Math.max(config.minVolume24h, 75000.0);
        boolean meetsLiquidity = token.getLiquidityUsd() >= Math.max(config.minLiquidityUsd, 15000.0);
        
        // Require reasonable price (filter out tokens with extremely low/high prices)
        boolean reasonablePrice = token.getPriceUsd() > 0.0000001 && token.getPriceUsd() < 100000;
        
        // Require positive momentum
        boolean positiveMomentum = token.getPriceChange24h() > 5.0;
        
        return meetsVolume && meetsLiquidity && reasonablePrice && positiveMomentum;
    }
    
    /**
     * Detect current market conditions (Bull/Bear/Sideways)
     * Adjusts strategy based on overall market momentum
     * 
     * FIXED: Cap extreme price changes to avoid skewed averages
     */
    private void detectMarketConditions() {
        try {
            // Sample top tokens to gauge overall market sentiment
            List<TokenInfo> tokens = dexScreenerClient.getTrendingTokens();
            
            if (tokens == null || tokens.isEmpty()) {
                return;
            }
            
            // Calculate average price change across top 50 tokens
            int sampleSize = Math.min(50, tokens.size());
            double totalPriceChange = 0.0;
            int positiveCount = 0;
            int validTokens = 0;
            
            for (int i = 0; i < sampleSize; i++) {
                TokenInfo token = tokens.get(i);
                double priceChange = token.getPriceChange24h();
                
                // Cap extreme values to avoid skewing the average
                // Memecoins can show 10,000%+ changes which aren't representative
                double cappedChange = Math.max(-100, Math.min(200, priceChange));
                
                totalPriceChange += cappedChange;
                validTokens++;
                
                if (priceChange > 0) {
                    positiveCount++;
                }
            }
            
            if (validTokens == 0) {
                return;
            }
            
            double avgPriceChange = totalPriceChange / validTokens;
            double bullishPercentage = (positiveCount * 100.0) / sampleSize;
            
            // Determine market condition
            MarketCondition previousCondition = currentMarketCondition;
            
            if (avgPriceChange > 20 && bullishPercentage > 70) {
                currentMarketCondition = MarketCondition.BULL;
            } else if (avgPriceChange < -10 || bullishPercentage < 30) {
                currentMarketCondition = MarketCondition.BEAR;
            } else {
                currentMarketCondition = MarketCondition.SIDEWAYS;
            }
            
            // Log condition changes
            if (previousCondition != currentMarketCondition) {
                logger.info("🌡️  Market Condition Changed: {} → {} (Avg: {}%, Bullish: {}%)", 
                    previousCondition, 
                    currentMarketCondition,
                    String.format("%.1f", avgPriceChange),
                    String.format("%.1f", bullishPercentage));
            }
            
        } catch (Exception e) {
            logger.error("Error detecting market conditions", e);
        }
    }
    
    /**
     * Calculate dynamic slippage tolerance based on token liquidity
     */
    private int calculateDynamicSlippage(TokenInfo token) {
        double liquidity = token.getLiquidityUsd();
        
        // High liquidity = lower slippage tolerance needed
        if (liquidity > 100_000) {
            return 50; // 0.5%
        } else if (liquidity > 50_000) {
            return 75; // 0.75%
        } else if (liquidity > 20_000) {
            return 100; // 1.0%
        } else if (liquidity > 10_000) {
            return 150; // 1.5%
        } else {
            return 200; // 2.0% for low liquidity
        }
    }
    
    /**
     * Calculate estimated transaction fees (Jupiter + Solana network)
     */
    private double calculateTransactionFees(double tradeValueUsd) {
        // Jupiter swap fee: ~0.2% of trade value
        double jupiterFee = tradeValueUsd * 0.002;
        
        // Solana network fee: ~0.01 SOL per transaction (~$2-3 at $200 SOL)
        double solanaFee = 0.01 * 200; // Assuming $200 per SOL (adjust based on actual price)
        
        return jupiterFee + solanaFee;
    }
    
    /**
     * Calculate smart position size based on token volatility and market conditions
     * PROFITABILITY IMPROVEMENT: Fee-aware position sizing
     */
    private double calculateSmartPositionSize(TokenInfo token) {
        // CRITICAL CHANGE: Increase minimum position size to make fees less impactful
        // At $20: fees are ~$2.04 (10.2% of position) - nearly impossible to profit
        // At $50: fees are ~$2.10 (4.2% of position) - much more reasonable
        // At $100: fees are ~$2.20 (2.2% of position) - ideal for profitability
        double baseSize = config.dryRunMode ? 50.0 : config.maxPositionSizeUsd;
        double priceChange24h = Math.abs(token.getPriceChange24h());
        
        // Adjust based on volatility - favor moderate volatility
        double volatilityMultiplier;
        if (priceChange24h > 100) {
            volatilityMultiplier = 0.6; // 60% - extremely volatile, reduce size
        } else if (priceChange24h > 50) {
            volatilityMultiplier = 0.9; // 90% - high volatility sweet spot
        } else if (priceChange24h > 30) {
            volatilityMultiplier = 1.0; // 100% - ideal volatility range
        } else if (priceChange24h > 20) {
            volatilityMultiplier = 0.8; // 80% - decent momentum
        } else {
            volatilityMultiplier = 0.7; // 70% - weak momentum, smaller size
        }
        
        // Adjust based on market conditions
        double marketMultiplier;
        switch (currentMarketCondition) {
            case BULL:
                marketMultiplier = 1.3; // 30% larger positions in bull market
                break;
            case BEAR:
                marketMultiplier = 0.6; // 40% smaller positions in bear market
                break;
            default:
                marketMultiplier = 1.0; // Normal size in sideways market
        }
        
        // Adjust based on token score (higher scores get larger positions)
        double scoreMultiplier = 1.0;
        if (token.getScore() > 85) {
            scoreMultiplier = 1.2; // 20% bonus for excellent tokens
        } else if (token.getScore() > 75) {
            scoreMultiplier = 1.1; // 10% bonus for good tokens
        }
        
        // Adjust based on liquidity (better liquidity = larger size)
        double liquidityMultiplier = 1.0;
        if (token.getLiquidityUsd() > 100_000) {
            liquidityMultiplier = 1.1; // 10% bonus for high liquidity
        }
        
        double finalSize = baseSize * volatilityMultiplier * marketMultiplier * scoreMultiplier * liquidityMultiplier;
        
        // Ensure we don't exceed max daily loss limit
        finalSize = Math.min(finalSize, config.maxDailyLossUsd - dailyLoss);
        
        // CRITICAL: Minimum $40 to keep fees under 5% of position
        return Math.max(40.0, finalSize);
    }
    
    /**
     * Get adjusted entry score threshold based on market conditions
     */
    private double getAdjustedEntryThreshold() {
        switch (currentMarketCondition) {
            case BULL:
                return config.minFinalScore * 0.85; // Lower threshold in bull market (55 instead of 65)
            case BEAR:
                return config.minFinalScore * 1.15; // Higher threshold in bear market (75 instead of 65)
            default:
                return config.minFinalScore; // Normal threshold in sideways market
        }
    }
    
    private void checkDailyReset() {
        Duration elapsed = Duration.between(lastDayReset, Instant.now());
        if (elapsed.toHours() >= 24) {
            logger.info("Resetting daily loss counter");
            dailyLoss = 0.0;
            lastDayReset = Instant.now();
        }
    }
    
    /**
     * Display comprehensive session summary with P&L breakdown
     */
    private void displaySessionSummary() {
        Duration sessionDuration = Duration.between(sessionStartTime, Instant.now());
        long hours = sessionDuration.toHours();
        long minutes = sessionDuration.toMinutes() % 60;
        
        // Calculate win/loss statistics
        int totalTrades = 0;
        int winningTrades = 0;
        int losingTrades = 0;
        double totalProfit = 0.0;
        double totalLoss = 0.0;
        double largestWin = 0.0;
        double largestLoss = 0.0;
        
        for (Trade trade : tradeHistory) {
            if (trade.getType() == Trade.TradeType.SELL) {
                totalTrades++;
                double pnl = trade.getProfitLossUsd();
                
                if (pnl > 0) {
                    winningTrades++;
                    totalProfit += pnl;
                    largestWin = Math.max(largestWin, pnl);
                } else if (pnl < 0) {
                    losingTrades++;
                    totalLoss += Math.abs(pnl);
                    largestLoss = Math.max(largestLoss, Math.abs(pnl));
                }
            }
        }
        
        double winRate = totalTrades > 0 ? (winningTrades * 100.0 / totalTrades) : 0.0;
        
        logger.info("\n╔═══════════════════════════════════════════════╗");
        logger.info("║          SESSION TRADING SUMMARY             ║");
        logger.info("╚═══════════════════════════════════════════════╝");
        logger.info("");
        logger.info("⏱️  Session Duration: {}h {}m", hours, minutes);
        logger.info("");
        logger.info("📊 TRADING PERFORMANCE:");
        logger.info("  • Total Trades (Sells): {}", totalTrades);
        logger.info("  • Winning Trades: {} ({}%)", winningTrades, String.format("%.1f", winRate));
        logger.info("  • Losing Trades: {}", losingTrades);
        logger.info("");
        logger.info("💵 PROFIT & LOSS:");
        logger.info("  • Total Profit: ${}", String.format("%.2f", totalProfit));
        logger.info("  • Total Loss: -${}", String.format("%.2f", totalLoss));
        logger.info("  • Total Fees Paid: ${}", String.format("%.2f", sessionFeesUsd));
        logger.info("  • Net P&L (after fees): ${}", String.format("%.2f", sessionPnL - sessionFeesUsd));
        logger.info("  • Largest Win: ${}", String.format("%.2f", largestWin));
        logger.info("  • Largest Loss: -${}", String.format("%.2f", largestLoss));
        logger.info("");
        
        String pnlSymbol = sessionPnL >= 0 ? "+" : "";
        String pnlEmoji = sessionPnL >= 0 ? "🟢" : "🔴";
        logger.info("💰 SESSION NET P&L: {}{} ${}",
                pnlEmoji,
                pnlSymbol,
                String.format("%.2f", sessionPnL));
        logger.info("");
        logger.info("════════════════════════════════════════════════");
        
        if (sessionPnL >= 0) {
            logger.info("✅ Profitable session! Great work!");
        } else {
            logger.info("⚠️  Session ended with a loss. Review your strategy.");
        }
        logger.info("════════════════════════════════════════════════\n");
    }
    
    private void logStatus() {
        String pnlColor = sessionPnL >= 0 ? "+" : "";
        logger.info("─────────────────────────────────────────");
        logger.info("Status | Positions: {}/{} | Session P&L: {}${} | Trades: {}", 
                activePositions.size(), 
                config.maxConcurrentPositions,
                pnlColor,
                String.format("%.2f", sessionPnL),
                tradeHistory.size());
        logger.info("─────────────────────────────────────────");
    }
    
    public Map<String, Position> getActivePositions() {
        return new HashMap<>(activePositions);
    }
    
    public List<Trade> getTradeHistory() {
        return new ArrayList<>(tradeHistory);
    }
    
    public double getTotalPnL() {
        return sessionPnL;
    }
    
    public double getSessionPnL() {
        return sessionPnL;
    }
}
