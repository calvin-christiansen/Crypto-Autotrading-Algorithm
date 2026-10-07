package com.solana.memecoin.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized configuration management for the Solana Memecoin Trader
 * Loads settings from pom.xml properties and .env file
 */
public class BotConfig {
    private static final Logger logger = LoggerFactory.getLogger(BotConfig.class);
    private static BotConfig instance;
    
    private final Dotenv dotenv;
    private final Properties pomProperties;
    
    // Scan Configuration
    public final int scanIntervalSeconds;
    public final double minVolume24h;
    public final double minLiquidityUsd;
    public final int minHolders;
    public final double minSentimentScore;
    public final double minFinalScore;
    public final int maxTokenAgeHours;
    
    // Risk Parameters
    public final double maxPositionSizeUsd;
    public final double stopLossPercent;
    public final double takeProfitPercent;
    public final double trailingStopPercent;
    public final int maxConcurrentPositions;
    public final double maxDailyLossUsd;
    
    // Scoring Weights
    public final double weightMomentum;
    public final double weightVolume;
    public final double weightLiquidity;
    public final double weightSentiment;
    public final double weightSafety;
    
    // API Endpoints
    public final String jupiterApiUrl;
    public final String dexScreenerApiUrl;
    public final String solanaRpcUrl;
    
    // Environment Variables
    public final String solanaPrivateKey;
    public final String solanaPublicKey;
    public final boolean tradingEnabled;
    public final boolean dryRunMode;
    public final String telegramBotToken;
    public final String telegramChatId;
    
    private BotConfig() {
        // Load .env file
        this.dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        
        // Load pom.xml properties
        this.pomProperties = loadPomProperties();
        
        // Initialize scan configuration
        this.scanIntervalSeconds = getIntProperty("bot.scan.interval.seconds", 60);
        this.minVolume24h = getDoubleProperty("bot.min.volume.24h", 50000.0);
        this.minLiquidityUsd = getDoubleProperty("bot.min.liquidity.usd", 10000.0);
        this.minHolders = getIntProperty("bot.min.holders", 50);
        this.minSentimentScore = getDoubleProperty("bot.min.sentiment.score", 0.6);
        this.minFinalScore = getDoubleProperty("bot.min.final.score", 65.0);
        this.maxTokenAgeHours = getIntProperty("bot.max.token.age.hours", 72);
        
        // Initialize risk parameters
        this.maxPositionSizeUsd = getDoubleProperty("bot.max.position.size.usd", 100.0);
        this.stopLossPercent = getDoubleProperty("bot.stop.loss.percent", 15.0);
        this.takeProfitPercent = getDoubleProperty("bot.take.profit.percent", 50.0);
        this.trailingStopPercent = getDoubleProperty("bot.trailing.stop.percent", 10.0);
        this.maxConcurrentPositions = getIntProperty("bot.max.concurrent.positions", 5);
        this.maxDailyLossUsd = getDoubleProperty("bot.max.daily.loss.usd", 500.0);
        
        // Initialize scoring weights
        this.weightMomentum = getDoubleProperty("bot.weight.momentum", 0.30);
        this.weightVolume = getDoubleProperty("bot.weight.volume", 0.25);
        this.weightLiquidity = getDoubleProperty("bot.weight.liquidity", 0.20);
        this.weightSentiment = getDoubleProperty("bot.weight.sentiment", 0.15);
        this.weightSafety = getDoubleProperty("bot.weight.safety", 0.10);
        
        // Initialize API endpoints
        this.jupiterApiUrl = getProperty("bot.api.jupiter.url", "https://api.jup.ag/v6");
        this.dexScreenerApiUrl = getProperty("bot.api.dexscreener.url", "https://api.dexscreener.com/latest");
        this.solanaRpcUrl = dotenv.get("SOLANA_RPC_URL", 
                getProperty("bot.api.solana.rpc.url", "https://api.mainnet-beta.solana.com"));
        
        // Initialize environment variables
        this.solanaPrivateKey = dotenv.get("SOLANA_PRIVATE_KEY", "");
        this.solanaPublicKey = dotenv.get("SOLANA_PUBLIC_KEY", "");
        this.tradingEnabled = Boolean.parseBoolean(dotenv.get("TRADING_ENABLED", "false"));
        this.dryRunMode = Boolean.parseBoolean(dotenv.get("DRY_RUN_MODE", "true"));
        this.telegramBotToken = dotenv.get("TELEGRAM_BOT_TOKEN", "");
        this.telegramChatId = dotenv.get("TELEGRAM_CHAT_ID", "");
        
        logConfiguration();
    }
    
    public static synchronized BotConfig getInstance() {
        if (instance == null) {
            instance = new BotConfig();
        }
        return instance;
    }
    
    private Properties loadPomProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("bot.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            logger.warn("Could not load bot.properties, using defaults");
        }
        return props;
    }
    
    private String getProperty(String key, String defaultValue) {
        return pomProperties.getProperty(key, defaultValue);
    }
    
    private int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private double getDoubleProperty(String key, double defaultValue) {
        try {
            return Double.parseDouble(getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private void logConfiguration() {
        logger.info("=== Bot Configuration Loaded ===");
        logger.info("Scan Interval: {} seconds", scanIntervalSeconds);
        logger.info("Min Volume 24h: ${}", minVolume24h);
        logger.info("Min Liquidity: ${}", minLiquidityUsd);
        logger.info("Max Position Size: ${}", maxPositionSizeUsd);
        logger.info("Stop Loss: {}%", stopLossPercent);
        logger.info("Take Profit: {}%", takeProfitPercent);
        logger.info("Trading Enabled: {}", tradingEnabled);
        logger.info("Dry Run Mode: {}", dryRunMode);
        logger.info("================================");
    }
    
    public boolean isWalletConfigured() {
        return solanaPrivateKey != null && !solanaPrivateKey.isEmpty()
                && solanaPublicKey != null && !solanaPublicKey.isEmpty();
    }
    
    public boolean isTelegramConfigured() {
        return telegramBotToken != null && !telegramBotToken.isEmpty()
                && telegramChatId != null && !telegramChatId.isEmpty();
    }
}
