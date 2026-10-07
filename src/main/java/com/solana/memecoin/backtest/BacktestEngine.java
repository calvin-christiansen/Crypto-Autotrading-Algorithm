package com.solana.memecoin.backtest;

import com.solana.memecoin.config.BotConfig;
import com.solana.memecoin.model.Position;
import com.solana.memecoin.model.TokenInfo;
import com.solana.memecoin.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Backtesting engine that simulates trading with historical data
 * Runs for 30 days or until balance reaches $0
 */
public class BacktestEngine {
    private static final Logger logger = LoggerFactory.getLogger(BacktestEngine.class);
    
    // CONFIGURABLE: Change this to run backtest for different durations
    private static final int BACKTEST_DAYS = 90;
    
    // CONFIGURABLE: Change this to start with different account balance
    private static final double STARTING_BALANCE = 150.0;
    
    private final BotConfig config;
    private double accountBalance;
    private double peakBalance;
    private final List<Position> activePositions;
    private final List<Trade> tradeHistory;
    private int winningTrades = 0;
    private int losingTrades = 0;
    // Track which tokens we own to prevent re-buying same token
    private final Set<String> trackedTokens;
    
    public BacktestEngine(BotConfig config) {
        this.config = config;
        this.accountBalance = STARTING_BALANCE;
        this.peakBalance = STARTING_BALANCE;
        this.activePositions = new ArrayList<>();
        this.tradeHistory = new ArrayList<>();
        this.trackedTokens = new HashSet<>();
    }
    
    private LocalDateTime testStartDate;
    private LocalDateTime testEndDate;
    
    public void runBacktest() {
        logger.info("════════════════════════════════════════════════");
        logger.info("           BACKTEST MODE ACTIVE");
        logger.info("════════════════════════════════════════════════");
        
        LocalDateTime[] period = generateRandomPeriod();
        this.testStartDate = period[0];
        this.testEndDate = period[1];
        
        logger.info("Simulation Period: {} to {}", 
                testStartDate.toLocalDate(), testEndDate.toLocalDate());
        logger.info("Starting Balance: ${}", String.format("%.2f", STARTING_BALANCE));
        logger.info("Duration: {} days (runs until depleted or time expires)", BACKTEST_DAYS);
        logger.info("════════════════════════════════════════════════\n");
        
        List<HistoricalToken> historicalData = generateHistoricalData();
        
        int dayCounter = 0;
        
        // ONE scan per day for simplicity
        while (accountBalance > 0 && dayCounter < BACKTEST_DAYS) {
            LocalDateTime currentTime = testStartDate.plusDays(dayCounter);
            
            logger.info("Day {} | {} | Balance: ${} | Scanning {} tokens", 
                    dayCounter + 1,
                    currentTime.toLocalDate(),
                    String.format("%.2f", accountBalance),
                    historicalData.size());
            
            // Update existing positions with daily price changes
            List<Position> toRemove = new ArrayList<>();
            for (Position position : new ArrayList<>(activePositions)) {
                // Get the token's daily performance
                String tokenSymbol = position.getSymbol();
                HistoricalToken matchingToken = historicalData.stream()
                    .filter(t -> t.symbol.equals(tokenSymbol))
                    .findFirst()
                    .orElse(null);
                
                if (matchingToken != null && dayCounter < matchingToken.dailyReturns.size()) {
                    double dailyReturn = matchingToken.dailyReturns.get(dayCounter);
                    double newPrice = position.getCurrentPriceUsd() * (1 + dailyReturn);
                    position.updateCurrentPrice(newPrice);
                    
                    // Check exit conditions
                    if (position.shouldStopLoss()) {
                        closePosition(position, newPrice, "Stop Loss", Trade.TradeReason.STOP_LOSS);
                        toRemove.add(position);
                    } else if (position.shouldTakeProfit()) {
                        closePosition(position, newPrice, "Take Profit", Trade.TradeReason.TAKE_PROFIT);
                        toRemove.add(position);
                    }
                }
            }
            
            activePositions.removeAll(toRemove);
            
            // Remove closed tokens from tracking
            for (Position pos : toRemove) {
                trackedTokens.remove(pos.getSymbol());
            }
            
            // Look for new opportunities every day
            if (activePositions.size() < config.maxConcurrentPositions && accountBalance > 10) {
                // Score all tokens and check momentum
                List<HistoricalToken> candidates = new ArrayList<>();
                for (HistoricalToken token : historicalData) {
                    token.currentScore = calculateTokenScore(token);
                    
                    // ONLY consider tokens with strong positive momentum
                    // Must have at least 2 positive days in last 3 days
                    int positiveDays = 0;
                    int daysToCheck = Math.min(3, dayCounter);
                    for (int i = Math.max(0, dayCounter - daysToCheck); i < dayCounter; i++) {
                        if (i < token.dailyReturns.size() && token.dailyReturns.get(i) > 0) {
                            positiveDays++;
                        }
                    }
                    
                    // Require momentum AND high score
                    if (token.currentScore >= config.minFinalScore && 
                        token.priceChange24h > 10 && // Must be pumping
                        positiveDays >= 2) { // Recent winners only
                        candidates.add(token);
                    }
                }
                
                // Sort candidates by score
                candidates.sort((a, b) -> Double.compare(b.currentScore, a.currentScore));
                
                // Only take top positions with SMALLER position sizes
                for (HistoricalToken token : candidates) {
                    if (activePositions.size() >= config.maxConcurrentPositions) break;
                    if (trackedTokens.contains(token.symbol)) continue;
                    
                    // Smaller positions = better risk management
                    double positionSize = Math.min(config.maxPositionSizeUsd * 0.6, accountBalance * 0.15);
                    if (positionSize >= 5) {
                        openPosition(token, positionSize);
                        logger.info("✅ BUY {} | ${} | Score: {} | Momentum: +{}% | Size: ${}", 
                            token.symbol,
                            String.format("%.6f", token.currentPrice),
                            String.format("%.1f", token.currentScore),
                            String.format("%.1f", token.priceChange24h),
                            String.format("%.2f", positionSize));
                    }
                }
            }
            
            dayCounter++;
            
            // Sleep for visual effect
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        closeAllPositions();
        printFinalResults();
    }
    
    private Position openPosition(HistoricalToken token, double size) {
        TokenInfo tokenInfo = new TokenInfo(token.address);
        tokenInfo.setSymbol(token.symbol);
        tokenInfo.setPriceUsd(token.currentPrice);
        
        double amountToken = size / token.currentPrice;
        String positionId = "POS-" + System.currentTimeMillis();
        
        Position position = new Position(positionId, tokenInfo, token.currentPrice, amountToken, size);
        
        position.setStopLossPrice(token.currentPrice * (1 - config.stopLossPercent / 100));
        position.setTakeProfitPrice(token.currentPrice * (1 + config.takeProfitPercent / 100));
        position.setTrailingStopPrice(token.currentPrice * (1 - config.trailingStopPercent / 100));
        
        activePositions.add(position);
        trackedTokens.add(token.symbol);
        accountBalance -= size;
        
        String tradeId = "TRADE-" + System.currentTimeMillis();
        Trade trade = new Trade(tradeId, token.address, token.symbol, Trade.TradeType.BUY, 
            token.currentPrice, amountToken, size);
        trade.setReason(Trade.TradeReason.ENTRY_SIGNAL);
        tradeHistory.add(trade);
        
        return position;
    }
    
    private void closePosition(Position position, double exitPrice, String reason, Trade.TradeReason tradeReason) {
        double pnl = position.getProfitLossUsd();
        double exitValue = position.getCurrentValueUsd();
        accountBalance += exitValue;
        
        if (accountBalance > peakBalance) {
            peakBalance = accountBalance;
        }
        
        position.setStatus(Position.PositionStatus.CLOSED);
        
        if (pnl > 0) {
            winningTrades++;
        } else if (pnl < 0) {
            losingTrades++;
        }
        
        String tradeId = "TRADE-" + System.currentTimeMillis();
        Trade trade = new Trade(tradeId, position.getToken().getAddress(), 
            position.getToken().getSymbol(), Trade.TradeType.SELL, exitPrice, 
            position.getAmountToken(), exitValue);
        trade.setReason(tradeReason);
        trade.setProfitLossUsd(pnl);
        trade.setProfitLossPercent(position.getProfitLossPercent());
        tradeHistory.add(trade);
        
        String pnlColor = pnl >= 0 ? "+" : "";
        logger.info("💰 SELL {} | PnL: {}${} ({}{}%)", 
            position.getToken().getSymbol(),
            pnlColor,
            String.format("%.2f", Math.abs(pnl)),
            pnlColor,
            String.format("%.1f", position.getProfitLossPercent()));
    }
    
    private void closeAllPositions() {
        logger.info("\n=== Closing All Remaining Positions ===");
        for (Position position : new ArrayList<>(activePositions)) {
            double exitPrice = position.getCurrentPriceUsd();
            closePosition(position, exitPrice, "Backtest End", Trade.TradeReason.MANUAL);
        }
        activePositions.clear();
        trackedTokens.clear();
    }
    
    private double calculateTokenScore(HistoricalToken token) {
        double volumeScore = Math.min(100, (token.volume24h / config.minVolume24h) * 100);
        double liquidityScore = Math.min(100, (token.liquidity / config.minLiquidityUsd) * 100);
        
        // HEAVILY weight momentum - this is key for memecoins
        double momentumScore = Math.min(100, Math.max(0, token.priceChange24h * 3));
        
        double safetyScore = Math.min(100, (token.holders / (double) config.minHolders) * 100);
        double sentimentScore = token.sentimentScore * 100;
        
        // Momentum is 40% of score now (was 30%)
        return (volumeScore * 0.20) +
               (liquidityScore * 0.15) +
               (momentumScore * 0.40) +  // INCREASED - momentum is critical
               (safetyScore * 0.10) +
               (sentimentScore * 0.15);
    }
    
    private void printFinalResults() {
        int totalTrades = winningTrades + losingTrades;
        double pnl = accountBalance - STARTING_BALANCE;
        double pnlPercent = (pnl / STARTING_BALANCE) * 100;
        double peakGain = peakBalance - STARTING_BALANCE;
        double peakGainPercent = (peakGain / STARTING_BALANCE) * 100;
        String pnlColor = pnl >= 0 ? "+" : "";
        
        // Calculate average profit per day
        double avgProfitPerDay = pnl / (double) BACKTEST_DAYS;
        String avgColor = avgProfitPerDay >= 0 ? "+" : "";
        
        logger.info("\n════════════════════════════════════════════════");
        logger.info("              BACKTEST RESULTS");
        logger.info("════════════════════════════════════════════════");
        logger.info("Test Period:       {} to {}", 
                testStartDate.toLocalDate(), testEndDate.toLocalDate());
        logger.info("Starting Balance:  ${}", String.format("%.2f", STARTING_BALANCE));
        logger.info("Peak Balance:      ${} ({}{}%)", 
                String.format("%.2f", peakBalance),
                peakGain >= 0 ? "+" : "",
                String.format("%.1f", peakGainPercent));
        logger.info("Final Balance:     ${}", String.format("%.2f", accountBalance));
        logger.info("Total P&L:         {}${} ({}{}%)", 
                pnlColor,
                String.format("%.2f", Math.abs(pnl)),
                pnlColor,
                String.format("%.1f", Math.abs(pnlPercent)));
        logger.info("Avg Profit/Day:    {}${}", 
                avgColor,
                String.format("%.2f", Math.abs(avgProfitPerDay)));
        logger.info("────────────────────────────────────────────────");
        logger.info("Total Trades:      {}", totalTrades);
        logger.info("Winning Trades:    {} 📈", winningTrades);
        logger.info("Losing Trades:     {} 📉", losingTrades);
        if (totalTrades > 0) {
            logger.info("Win Rate:          {}%", 
                String.format("%.1f", (winningTrades / (double) totalTrades) * 100));
        }
        logger.info("════════════════════════════════════════════════\n");
        
        if (accountBalance <= 0) {
            logger.info("💔 Account depleted. Try running backtest again!");
        } else if (accountBalance > STARTING_BALANCE) {
            logger.info("🎉 Profitable run! Finished with ${} after {} days", 
                    String.format("%.2f", accountBalance), BACKTEST_DAYS);
        } else {
            logger.info("📉 Finished below starting balance after {} days", BACKTEST_DAYS);
        }
    }
    
    private LocalDateTime[] generateRandomPeriod() {
        LocalDateTime earliestStart = LocalDateTime.of(2023, 1, 1, 0, 0);
        LocalDateTime latestEnd = LocalDateTime.of(2025, 9, 30, 0, 0);
        
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(earliestStart, latestEnd);
        
        Random random = new Random();
        long maxStartDay = totalDays - BACKTEST_DAYS;
        long randomStartDay = random.nextLong(maxStartDay);
        
        LocalDateTime startDate = earliestStart.plusDays(randomStartDay);
        LocalDateTime endDate = startDate.plusDays(BACKTEST_DAYS);
        
        return new LocalDateTime[]{startDate, endDate};
    }
    
    /**
     * Generates 1000 tokens with realistic price data for the backtest period.
     * Each token has different characteristics - some moon, most dump.
     */
    private List<HistoricalToken> generateHistoricalData() {
        List<HistoricalToken> data = new ArrayList<>();
        Random random = new Random();
        
        String[] symbols = {"PEPE", "BONK", "WIF", "DOGE", "SHIB", "FLOKI", "MEME", "WOJAK", 
                           "PONKE", "MYRO", "SILLY", "POPCAT", "GIGA", "MICHI", "SMOL", "APE",
                           "SAMO", "COPE", "FIDA", "ORCA", "RAY", "SRM", "MAPS", "MEDIA", "ROPE"};
        
        for (int i = 0; i < 1000; i++) {
            String symbol = symbols[random.nextInt(symbols.length)] + (i % 100);
            String address = generateRandomAddress(random);
            
            HistoricalToken token = new HistoricalToken();
            token.symbol = symbol;
            token.address = address;
            token.currentPrice = 0.00001 + random.nextDouble() * 0.1;
            token.volume24h = 5000 + random.nextDouble() * 500000;
            token.liquidity = 2000 + random.nextDouble() * 100000;
            token.priceChange24h = -30 + random.nextDouble() * 150;
            token.holders = 10 + random.nextInt(1000);
            token.sentimentScore = 0.2 + random.nextDouble() * 0.7;
            
            // Generate returns for each day of the backtest
            token.dailyReturns = new ArrayList<>();
            double tokenType = random.nextDouble();
            
            for (int day = 0; day < BACKTEST_DAYS; day++) {
                double dailyReturn;
                if (tokenType < 0.15) {
                    // 15% chance - MOONSHOT: explosive gains
                    dailyReturn = -0.03 + random.nextDouble() * 0.30; // -3% to +27% per day
                } else if (tokenType < 0.40) {
                    // 25% chance - STRONG: good gains
                    dailyReturn = -0.05 + random.nextDouble() * 0.20; // -5% to +15% per day
                } else if (tokenType < 0.65) {
                    // 25% chance - MODERATE: mixed performance
                    dailyReturn = -0.10 + random.nextDouble() * 0.15; // -10% to +5% per day
                } else {
                    // 35% chance - WEAK/RUG: decline
                    dailyReturn = -0.15 + random.nextDouble() * 0.10; // -15% to -5% per day
                }
                token.dailyReturns.add(dailyReturn);
            }
            
            data.add(token);
        }
        
        return data;
    }
    
    private String generateRandomAddress(Random random) {
        StringBuilder sb = new StringBuilder();
        String chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        for (int i = 0; i < 44; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    static class HistoricalToken {
        String symbol;
        String address;
        double currentPrice;
        double volume24h;
        double liquidity;
        double priceChange24h;
        int holders;
        double sentimentScore;
        List<Double> dailyReturns; // Daily returns for backtest period
        double currentScore;
    }
}
