package com.solana.memecoin.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solana.memecoin.config.BotConfig;
import com.solana.memecoin.model.TokenInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client for interacting with DexScreener API to get token data
 */
public class DexScreenerClient {
    private static final Logger logger = LoggerFactory.getLogger(DexScreenerClient.class);
    private final OkHttpClient httpClient;
    private final BotConfig config;
    private final String baseUrl;
    
    public DexScreenerClient(BotConfig config) {
        this.config = config;
        this.baseUrl = config.dexScreenerApiUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Get trending tokens on Solana - fetches up to 1000 tokens with diversity
     */
    public List<TokenInfo> getTrendingTokens() {
        List<TokenInfo> tokens = new ArrayList<>();
        
        try {
            // Expanded search strategy for maximum token diversity
            
            // 1. Popular meme tokens
            String[] memeKeywords = {"PEPE", "BONK", "WIF", "DOGE", "SHIB", "MEME", "WOJAK", 
                                    "FLOKI", "PONKE", "MYRO", "POPCAT", "GIGA", "MICHI",
                                    "TRUMP", "ELON", "MOON", "PUMP", "ROCKET", "LAMBO", "SAFE",
                                    "BABY", "MINI", "KING", "QUEEN", "LORD", "GOD", "CHAD"};
            
            // 2. Trending themes and narratives
            String[] themeKeywords = {"AI", "GAMING", "DEFI", "NFT", "DAO", "METAVERSE",
                                     "ORDINALS", "INSCRIPTION", "FROG", "CAT", "DOG", "BRETT",
                                     "MAGA", "BIDEN", "PEPE", "WOJAK", "GIGACHAD", "SIGMA"};
            
            // 3. Multi-character diversity searches (removed single chars - DexScreener min length is 2)
            String[] diversityQueries = {"69", "420", "100", "1000", "APE", "BANK", "COIN", "TOKEN",
                                        "BULL", "BEAR", "RAGE", "COPE", "DEGEN", "ALPHA", "BETA",
                                        "KEKW", "GIGACHAD", "SOL", "SOLANA", "BASED", "CRINGE"};
            
            // 4. Common token patterns
            String[] patternQueries = {"INU", "SWAP", "FINANCE", "PROTOCOL", "NETWORK",
                                      "CHAIN", "VERSE", "LAND", "FUND", "CAPITAL", "LABS",
                                      "TECH", "COIN", "TOKEN", "MONEY", "CASH", "GOLD"};
            
            // 5. Animal and character themes
            String[] animalKeywords = {"FROG", "DOG", "CAT", "BIRD", "FISH", "SNAKE", "DRAGON",
                                      "LION", "TIGER", "WOLF", "BEAR", "BULL", "MONKEY", "APE"};
            
            // 6. Action and emotion keywords
            String[] actionKeywords = {"MOON", "ROCKET", "PUMP", "DUMP", "RICH", "POOR", "WIN",
                                      "LOSE", "BUY", "SELL", "HOLD", "HODL", "YEET", "SEND"};
            
            // Combine all keywords
            List<String> allKeywords = new ArrayList<>();
            allKeywords.addAll(java.util.Arrays.asList(memeKeywords));
            allKeywords.addAll(java.util.Arrays.asList(themeKeywords));
            allKeywords.addAll(java.util.Arrays.asList(diversityQueries));
            allKeywords.addAll(java.util.Arrays.asList(patternQueries));
            allKeywords.addAll(java.util.Arrays.asList(animalKeywords));
            allKeywords.addAll(java.util.Arrays.asList(actionKeywords));
            
            // Shuffle for randomization each scan
            java.util.Collections.shuffle(allKeywords);
            
            // Search with keywords until we reach 1000 tokens
            for (String keyword : allKeywords) {
                if (tokens.size() >= 1200) break; // Get extra to account for deduplication
                tokens.addAll(searchTokens(keyword));
                try { Thread.sleep(150); } catch (InterruptedException e) { }
            }
            
            // Remove duplicates by address
            List<TokenInfo> uniqueTokens = new ArrayList<>();
            java.util.Set<String> seenAddresses = new java.util.HashSet<>();
            for (TokenInfo token : tokens) {
                if (seenAddresses.add(token.getAddress())) {
                    uniqueTokens.add(token);
                    if (uniqueTokens.size() >= 1000) break;
                }
            }
            
            // Shuffle the final list for randomization
            java.util.Collections.shuffle(uniqueTokens);
            
            logger.info("Fetched {} unique tokens from DexScreener", uniqueTokens.size());
            return uniqueTokens;
            
        } catch (Exception e) {
            logger.error("Error fetching trending tokens from DexScreener", e);
        }
        
        return tokens;
    }
    
    /**
     * Fetch pairs from a specific endpoint
     */
    private List<TokenInfo> fetchPairs(String endpoint) {
        List<TokenInfo> tokens = new ArrayList<>();
        
        try {
            String url = baseUrl + endpoint;
            logger.info("Fetching from: {}", url);
            
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                logger.info("Response code: {}", response.code());
                
                if (!response.isSuccessful()) {
                    logger.error("API request failed with code: {} for {}", response.code(), url);
                    if (response.body() != null) {
                        logger.error("Response body: {}", response.body().string());
                    }
                    return tokens;
                }
                
                String jsonData = response.body().string();
                logger.debug("Response length: {} characters", jsonData.length());
                
                JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
                
                if (jsonObject.has("pairs")) {
                    JsonArray pairs = jsonObject.getAsJsonArray("pairs");
                    logger.info("Found {} pairs in response", pairs.size());
                    
                    for (JsonElement element : pairs) {
                        try {
                            TokenInfo token = parseTokenFromPair(element.getAsJsonObject());
                            if (token != null) {
                                tokens.add(token);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to parse token: {}", e.getMessage());
                        }
                    }
                } else {
                    logger.warn("No 'pairs' field in response. Available fields: {}", jsonObject.keySet());
                }
            }
        } catch (IOException e) {
            logger.error("IOException fetching pairs from {}: {}", endpoint, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching pairs: {}", e.getMessage(), e);
        }
        
        return tokens;
    }
    
    /**
     * Get token information by address
     */
    public TokenInfo getTokenInfo(String tokenAddress) {
        try {
            String url = baseUrl + "/dex/tokens/" + tokenAddress;
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("DexScreener token info request failed: {}", response.code());
                    return null;
                }
                
                String jsonData = response.body().string();
                JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
                
                if (jsonObject.has("pairs") && jsonObject.getAsJsonArray("pairs").size() > 0) {
                    JsonObject pair = jsonObject.getAsJsonArray("pairs").get(0).getAsJsonObject();
                    return parseTokenFromPair(pair);
                }
            }
        } catch (IOException e) {
            logger.error("Error fetching token info from DexScreener for address: {}", tokenAddress, e);
        }
        
        return null;
    }
    
    /**
     * Search for tokens by symbol or name
     */
    public List<TokenInfo> searchTokens(String query) {
        List<TokenInfo> tokens = new ArrayList<>();
        
        try {
            String url = baseUrl + "/dex/search?q=" + query;
            
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("DexScreener search request failed with code: {} for query: {}", response.code(), query);
                    return tokens;
                }
                
                String jsonData = response.body().string();
                JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
                
                if (jsonObject.has("pairs")) {
                    JsonArray pairs = jsonObject.getAsJsonArray("pairs");
                    
                    for (JsonElement element : pairs) {
                        try {
                            JsonObject pair = element.getAsJsonObject();
                            // Check if this is a Solana chain token
                            String chainId = pair.has("chainId") ? pair.get("chainId").getAsString() : "";
                            
                            if ("solana".equalsIgnoreCase(chainId)) {
                                TokenInfo token = parseTokenFromPair(pair);
                                if (token != null) {
                                    tokens.add(token);
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Error parsing token from DexScreener search: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("IOException searching tokens for query '{}': {}", query, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error searching tokens for query '{}': {}", query, e.getMessage());
        }
        
        return tokens;
    }
    
    private TokenInfo parseTokenFromPair(JsonObject pair) {
        try {
            JsonObject baseToken = pair.getAsJsonObject("baseToken");
            String address = baseToken.get("address").getAsString();
            
            TokenInfo token = new TokenInfo(address);
            token.setSymbol(baseToken.get("symbol").getAsString());
            token.setName(baseToken.get("name").getAsString());
            
            // Price data
            if (pair.has("priceUsd")) {
                token.setPriceUsd(pair.get("priceUsd").getAsDouble());
            }
            
            // Volume
            if (pair.has("volume")) {
                JsonObject volume = pair.getAsJsonObject("volume");
                if (volume.has("h24")) {
                    token.setVolume24h(volume.get("h24").getAsDouble());
                }
            }
            
            // Liquidity
            if (pair.has("liquidity")) {
                JsonObject liquidity = pair.getAsJsonObject("liquidity");
                if (liquidity.has("usd")) {
                    token.setLiquidityUsd(liquidity.get("usd").getAsDouble());
                }
            }
            
            // Price changes
            if (pair.has("priceChange")) {
                JsonObject priceChange = pair.getAsJsonObject("priceChange");
                if (priceChange.has("h1")) {
                    token.setPriceChange1h(priceChange.get("h1").getAsDouble());
                }
                if (priceChange.has("h24")) {
                    token.setPriceChange24h(priceChange.get("h24").getAsDouble());
                }
            }
            
            // Market cap
            if (pair.has("marketCap")) {
                token.setMarketCapUsd(pair.get("marketCap").getAsDouble());
            }
            
            // DEX info
            if (pair.has("dexId")) {
                token.setDexName(pair.get("dexId").getAsString());
            }
            
            if (pair.has("pairAddress")) {
                token.setPairAddress(pair.get("pairAddress").getAsString());
            }
            
            // Creation time
            if (pair.has("pairCreatedAt")) {
                long timestamp = pair.get("pairCreatedAt").getAsLong();
                token.setCreatedAt(Instant.ofEpochMilli(timestamp));
            }
            
            return token;
        } catch (Exception e) {
            logger.error("Error parsing token from pair data", e);
            return null;
        }
    }
}
