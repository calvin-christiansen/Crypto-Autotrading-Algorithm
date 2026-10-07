package com.solana.memecoin;

import com.solana.memecoin.backtest.BacktestEngine;
import com.solana.memecoin.config.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Solana Memecoin Trader Bot
 * If no wallet keys are configured, runs backtesting mode
 * Otherwise, runs live trading mode
 */
public class MemecoinTraderBot {
    private static final Logger logger = LoggerFactory.getLogger(MemecoinTraderBot.class);
    
    public static void main(String[] args) {
        logger.info("╔═══════════════════════════════════════════════╗");
        logger.info("║   SOLANA MEMECOIN AUTOTRADER v1.0.0          ║");
        logger.info("║   Autonomous Java-based Crypto Trading Bot   ║");
        logger.info("╚═══════════════════════════════════════════════╝\n");
        
        try {
            // Load configuration
            BotConfig config = BotConfig.getInstance();
            
            // Check if wallet is configured
            if (!config.isWalletConfigured()) {
                logger.info("⚠️  No wallet keys detected in .env file");
                logger.info("🔄 Switching to BACKTEST MODE");
                logger.info("   Starting with $50, target: $100\n");
                
                runBacktestMode(config);
            } else {
                logger.info("✅ Wallet configured");
                logger.info("🚀 Starting LIVE TRADING MODE");
                logger.info("   Trading Enabled: {}", config.tradingEnabled);
                logger.info("   Dry Run Mode: {}\n", config.dryRunMode);
                
                runLiveTradingMode(config);
            }
            
        } catch (Exception e) {
            logger.error("Fatal error in bot execution", e);
            System.exit(1);
        }
    }
    
    private static void runBacktestMode(BotConfig config) {
        logger.info("════════════════════════════════════════════════");
        logger.info("           BACKTESTING MODE ACTIVE");
        logger.info("════════════════════════════════════════════════");
        logger.info("This mode simulates trading with historical data");
        logger.info("No real money or wallet required");
        logger.info("════════════════════════════════════════════════\n");
        
        BacktestEngine backtester = new BacktestEngine(config);
        backtester.runBacktest();
        
        logger.info("\n✓ Backtest Complete!");
        logger.info("To run live trading:");
        logger.info("  1. Copy .env.example to .env");
        logger.info("  2. Add your SOLANA_PRIVATE_KEY and SOLANA_PUBLIC_KEY");
        logger.info("  3. Run the bot again");
    }
    
    private static void runLiveTradingMode(BotConfig config) {
        logger.info("════════════════════════════════════════════════");
        logger.info("         LIVE TRADING MODE ACTIVE");
        logger.info("════════════════════════════════════════════════");
        logger.info("Using DexScreener for token discovery");
        logger.info("Using Jupiter for trade execution");
        logger.info("════════════════════════════════════════════════\n");
        
        logger.info("Current Configuration:");
        logger.info("  - Max Position Size: ${}", config.maxPositionSizeUsd);
        logger.info("  - Stop Loss: {}%", config.stopLossPercent);
        logger.info("  - Take Profit: {}%", config.takeProfitPercent);
        logger.info("  - Max Concurrent Positions: {}", config.maxConcurrentPositions);
        logger.info("  - Max Daily Loss: ${}", config.maxDailyLossUsd);
        logger.info("  - Scan Interval: {} seconds", config.scanIntervalSeconds);
        logger.info("\nScoring Weights:");
        logger.info("  - Momentum: {}%", config.weightMomentum * 100);
        logger.info("  - Volume: {}%", config.weightVolume * 100);
        logger.info("  - Liquidity: {}%", config.weightLiquidity * 100);
        logger.info("  - Sentiment: {}%", config.weightSentiment * 100);
        logger.info("  - Safety: {}%", config.weightSafety * 100);
        logger.info("\nFilters:");
        logger.info("  - Min Volume 24h: ${}", config.minVolume24h);
        logger.info("  - Min Liquidity: ${}", config.minLiquidityUsd);
        logger.info("  - Min Final Score: {}", config.minFinalScore);
        
        if (config.dryRunMode) {
            logger.info("\n⚠️  DRY RUN MODE ENABLED");
            logger.info("   Trades will be simulated (no real transactions)");
        } else {
            logger.warn("\n🚨 LIVE TRADING MODE - REAL MONEY AT RISK!");
            logger.warn("   Set DRY_RUN_MODE=true in .env to simulate trades");
        }
        
        logger.info("\n💡 Press Ctrl+C to stop the bot and close all positions\n");
        
        // Create and start live trading engine
        com.solana.memecoin.trading.LiveTradingEngine tradingEngine = 
                new com.solana.memecoin.trading.LiveTradingEngine(config);
        
        // Add shutdown hook to gracefully close positions
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\n🛑 Shutdown signal received");
            tradingEngine.stop();
            logger.info("All positions closed. Goodbye!");
        }));
        
        // Start the trading engine
        tradingEngine.start();
    }
}
