package com.solana.memecoin.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents comprehensive information about a Solana token
 */
public class TokenInfo {
    private String address;
    private String symbol;
    private String name;
    private double priceUsd;
    private double volume24h;
    private double liquidityUsd;
    private double priceChange1h;
    private double priceChange24h;
    private int holders;
    private Instant createdAt;
    private double marketCapUsd;
    
    // Scoring metrics
    private double momentumScore;
    private double volumeScore;
    private double liquidityScore;
    private double sentimentScore;
    private double safetyScore;
    private double finalScore;
    
    // Metadata
    private String dexName;
    private String pairAddress;
    private boolean isVerified;
    
    public TokenInfo(String address) {
        this.address = address;
    }
    
    // Getters and Setters
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public double getPriceUsd() {
        return priceUsd;
    }
    
    public void setPriceUsd(double priceUsd) {
        this.priceUsd = priceUsd;
    }
    
    public double getVolume24h() {
        return volume24h;
    }
    
    public void setVolume24h(double volume24h) {
        this.volume24h = volume24h;
    }
    
    public double getLiquidityUsd() {
        return liquidityUsd;
    }
    
    public void setLiquidityUsd(double liquidityUsd) {
        this.liquidityUsd = liquidityUsd;
    }
    
    public double getPriceChange1h() {
        return priceChange1h;
    }
    
    public void setPriceChange1h(double priceChange1h) {
        this.priceChange1h = priceChange1h;
    }
    
    public double getPriceChange24h() {
        return priceChange24h;
    }
    
    public void setPriceChange24h(double priceChange24h) {
        this.priceChange24h = priceChange24h;
    }
    
    public int getHolders() {
        return holders;
    }
    
    public void setHolders(int holders) {
        this.holders = holders;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public double getMarketCapUsd() {
        return marketCapUsd;
    }
    
    public void setMarketCapUsd(double marketCapUsd) {
        this.marketCapUsd = marketCapUsd;
    }
    
    public double getMomentumScore() {
        return momentumScore;
    }
    
    public void setMomentumScore(double momentumScore) {
        this.momentumScore = momentumScore;
    }
    
    public double getVolumeScore() {
        return volumeScore;
    }
    
    public void setVolumeScore(double volumeScore) {
        this.volumeScore = volumeScore;
    }
    
    public double getLiquidityScore() {
        return liquidityScore;
    }
    
    public void setLiquidityScore(double liquidityScore) {
        this.liquidityScore = liquidityScore;
    }
    
    public double getSentimentScore() {
        return sentimentScore;
    }
    
    public void setSentimentScore(double sentimentScore) {
        this.sentimentScore = sentimentScore;
    }
    
    public double getSafetyScore() {
        return safetyScore;
    }
    
    public void setSafetyScore(double safetyScore) {
        this.safetyScore = safetyScore;
    }
    
    public double getFinalScore() {
        return finalScore;
    }
    
    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
    }
    
    // Alias methods for compatibility
    public double getScore() {
        return finalScore;
    }
    
    public void setScore(double score) {
        this.finalScore = score;
    }
    
    public String getDexName() {
        return dexName;
    }
    
    public void setDexName(String dexName) {
        this.dexName = dexName;
    }
    
    public String getPairAddress() {
        return pairAddress;
    }
    
    public void setPairAddress(String pairAddress) {
        this.pairAddress = pairAddress;
    }
    
    public boolean isVerified() {
        return isVerified;
    }
    
    public void setVerified(boolean verified) {
        isVerified = verified;
    }
    
    public long getAgeHours() {
        if (createdAt == null) {
            return 0;
        }
        return (Instant.now().getEpochSecond() - createdAt.getEpochSecond()) / 3600;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenInfo tokenInfo = (TokenInfo) o;
        return Objects.equals(address, tokenInfo.address);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
    
    @Override
    public String toString() {
        return String.format("TokenInfo{address='%s', symbol='%s', price=$%.8f, volume=$%.2f, score=%.2f}",
                address, symbol, priceUsd, volume24h, finalScore);
    }
}
