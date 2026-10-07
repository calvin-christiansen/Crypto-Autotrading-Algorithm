package com.solana.memecoin.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solana.memecoin.config.BotConfig;
import com.solana.memecoin.wallet.SolanaWalletManager;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Client for Jupiter aggregator API - handles swap quotes and execution
 * Integrated with Solana wallet for real trade execution
 */
public class JupiterClient {
    private static final Logger logger = LoggerFactory.getLogger(JupiterClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
    private static final String SOL_MINT = "So11111111111111111111111111111111111111112";
    
    private final OkHttpClient httpClient;
    private final BotConfig config;
    private final String baseUrl;
    private final SolanaWalletManager walletManager;
    
    public JupiterClient(BotConfig config) {
        this.config = config;
        this.baseUrl = config.jupiterApiUrl;
        this.walletManager = new SolanaWalletManager(config);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    public JupiterClient(BotConfig config, SolanaWalletManager walletManager) {
        this.config = config;
        this.baseUrl = config.jupiterApiUrl;
        this.walletManager = walletManager;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Get a quote for swapping tokens
     * @param inputMint Input token mint address
     * @param outputMint Output token mint address
     * @param amount Amount in lamports/smallest unit
     * @param slippageBps Slippage in basis points (50 = 0.5%)
     * @return Quote JSON object or null if failed
     */
    public JsonObject getQuote(String inputMint, String outputMint, long amount, int slippageBps) {
        try {
            String url = String.format("%s/quote?inputMint=%s&outputMint=%s&amount=%d&slippageBps=%d",
                    baseUrl, inputMint, outputMint, amount, slippageBps);
            
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    logger.warn("Jupiter quote request failed: {} for URL: {} - Response: {}", 
                            response.code(), url, errorBody);
                    return null;
                }
                
                String jsonData = response.body().string();
                return JsonParser.parseString(jsonData).getAsJsonObject();
            }
        } catch (IOException e) {
            logger.error("Error getting quote from Jupiter", e);
            return null;
        }
    }
    
    /**
     * Get swap quote for buying a token with USDC
     */
    public JsonObject getBuyQuote(String tokenMint, double usdcAmount, int slippageBps) {
        // Convert USDC amount to lamports (6 decimals)
        long amountLamports = (long) (usdcAmount * 1_000_000);
        return getQuote(USDC_MINT, tokenMint, amountLamports, slippageBps);
    }
    
    /**
     * Get swap quote for selling a token for USDC
     */
    public JsonObject getSellQuote(String tokenMint, double tokenAmount, int decimals, int slippageBps) {
        // Convert token amount to lamports based on decimals
        long amountLamports = (long) (tokenAmount * Math.pow(10, decimals));
        return getQuote(tokenMint, USDC_MINT, amountLamports, slippageBps);
    }
    
    /**
     * Get serialized swap transaction
     * @param quoteResponse The quote response from getQuote()
     * @param userPublicKey User's wallet public key
     * @return Swap transaction data or null if failed
     */
    public JsonObject getSwapTransaction(JsonObject quoteResponse, String userPublicKey) {
        try {
            String url = baseUrl + "/swap";
            
            JsonObject requestBody = new JsonObject();
            requestBody.add("quoteResponse", quoteResponse);
            requestBody.addProperty("userPublicKey", userPublicKey);
            requestBody.addProperty("wrapAndUnwrapSol", true);
            
            RequestBody body = RequestBody.create(requestBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("Jupiter swap transaction request failed: {}", response.code());
                    return null;
                }
                
                String jsonData = response.body().string();
                return JsonParser.parseString(jsonData).getAsJsonObject();
            }
        } catch (IOException e) {
            logger.error("Error getting swap transaction from Jupiter", e);
            return null;
        }
    }
    
    /**
     * Get price for a token in USD
     */
    public Double getTokenPriceUsd(String tokenMint) {
        try {
            // Use a small amount to get price (1 token)
            JsonObject quote = getQuote(tokenMint, USDC_MINT, 1_000_000_000, 50);
            
            if (quote != null && quote.has("outAmount")) {
                long outAmount = quote.get("outAmount").getAsLong();
                // USDC has 6 decimals
                return outAmount / 1_000_000.0;
            }
        } catch (Exception e) {
            logger.error("Error getting token price from Jupiter", e);
        }
        
        return null;
    }
    
    /**
     * Simulate a swap to estimate output
     */
    public JsonObject simulateSwap(String inputMint, String outputMint, long amount) {
        return getQuote(inputMint, outputMint, amount, 50);
    }
    
    /**
     * Get list of tokens available on Jupiter
     */
    public JsonArray getTokenList() {
        try {
            String url = baseUrl + "/tokens";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("Jupiter token list request failed: {}", response.code());
                    return null;
                }
                
                String jsonData = response.body().string();
                return JsonParser.parseString(jsonData).getAsJsonArray();
            }
        } catch (IOException e) {
            logger.error("Error getting token list from Jupiter", e);
            return null;
        }
    }
    
    /**
     * Execute a buy trade (swap USDC for token)
     * @param tokenMint Token to buy
     * @param usdcAmount Amount of USDC to spend
     * @param slippageBps Slippage tolerance in basis points
     * @return Transaction signature or null if failed
     */
    public String executeBuy(String tokenMint, double usdcAmount, int slippageBps) {
        logger.info("Executing BUY: {} USDC -> {}", usdcAmount, tokenMint);
        
        if (config.dryRunMode) {
            logger.info("DRY RUN MODE: Simulating buy transaction");
            return "dry-run-buy-" + System.currentTimeMillis();
        }
        
        try {
            // Get quote
            JsonObject quote = getBuyQuote(tokenMint, usdcAmount, slippageBps);
            if (quote == null) {
                logger.error("Failed to get quote for buy");
                return null;
            }
            
            // Get swap transaction
            JsonObject swapTx = getSwapTransaction(quote, walletManager.getPublicKey());
            if (swapTx == null || !swapTx.has("swapTransaction")) {
                logger.error("Failed to get swap transaction");
                return null;
            }
            
            String serializedTx = swapTx.get("swapTransaction").getAsString();
            
            // Submit transaction
            String signature = walletManager.submitTransaction(serializedTx);
            if (signature != null) {
                logger.info("Buy transaction submitted: {}", signature);
                
                // Wait for confirmation
                boolean confirmed = walletManager.waitForConfirmation(signature, 30);
                if (confirmed) {
                    logger.info("Buy transaction confirmed successfully");
                    return signature;
                } else {
                    logger.warn("Buy transaction confirmation timeout");
                }
            }
            
            return signature;
        } catch (Exception e) {
            logger.error("Error executing buy", e);
            return null;
        }
    }
    
    /**
     * Execute a sell trade (swap token for USDC)
     * @param tokenMint Token to sell
     * @param tokenAmount Amount of tokens to sell
     * @param decimals Token decimals
     * @param slippageBps Slippage tolerance in basis points
     * @return Transaction signature or null if failed
     */
    public String executeSell(String tokenMint, double tokenAmount, int decimals, int slippageBps) {
        logger.info("Executing SELL: {} tokens of {}", tokenAmount, tokenMint);
        
        if (config.dryRunMode) {
            logger.info("DRY RUN MODE: Simulating sell transaction");
            return "dry-run-sell-" + System.currentTimeMillis();
        }
        
        try {
            // Get quote
            JsonObject quote = getSellQuote(tokenMint, tokenAmount, decimals, slippageBps);
            if (quote == null) {
                logger.error("Failed to get quote for sell");
                return null;
            }
            
            // Get swap transaction
            JsonObject swapTx = getSwapTransaction(quote, walletManager.getPublicKey());
            if (swapTx == null || !swapTx.has("swapTransaction")) {
                logger.error("Failed to get swap transaction");
                return null;
            }
            
            String serializedTx = swapTx.get("swapTransaction").getAsString();
            
            // Submit transaction
            String signature = walletManager.submitTransaction(serializedTx);
            if (signature != null) {
                logger.info("Sell transaction submitted: {}", signature);
                
                // Wait for confirmation
                boolean confirmed = walletManager.waitForConfirmation(signature, 30);
                if (confirmed) {
                    logger.info("Sell transaction confirmed successfully");
                    return signature;
                } else {
                    logger.warn("Sell transaction confirmation timeout");
                }
            }
            
            return signature;
        } catch (Exception e) {
            logger.error("Error executing sell", e);
            return null;
        }
    }
    
    /**
     * Estimate output amount for a swap
     */
    public double estimateSwapOutput(String inputMint, String outputMint, double inputAmount, int inputDecimals) {
        try {
            long amountLamports = (long) (inputAmount * Math.pow(10, inputDecimals));
            JsonObject quote = getQuote(inputMint, outputMint, amountLamports, 50);
            
            if (quote != null && quote.has("outAmount")) {
                long outAmount = quote.get("outAmount").getAsLong();
                // Assuming output is USDC with 6 decimals
                return outAmount / 1_000_000.0;
            }
        } catch (Exception e) {
            logger.error("Error estimating swap output", e);
        }
        
        return 0.0;
    }
    
    /**
     * Calculate price impact for a trade
     */
    public double calculatePriceImpact(String inputMint, String outputMint, double inputAmount, int inputDecimals) {
        try {
            long amountLamports = (long) (inputAmount * Math.pow(10, inputDecimals));
            JsonObject quote = getQuote(inputMint, outputMint, amountLamports, 50);
            
            if (quote != null && quote.has("priceImpactPct")) {
                return quote.get("priceImpactPct").getAsDouble();
            }
        } catch (Exception e) {
            logger.error("Error calculating price impact", e);
        }
        
        return 0.0;
    }
    
    public SolanaWalletManager getWalletManager() {
        return walletManager;
    }
}
