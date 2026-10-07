package com.solana.memecoin.model;

import java.time.Instant;

/**
 * Represents a completed trade transaction
 */
public class Trade {
    private String id;
    private String tokenAddress;
    private String tokenSymbol;
    private TradeType type;
    private double priceUsd;
    private double amountToken;
    private double amountUsd;
    private double profitLossUsd;
    private double profitLossPercent;
    private double transactionFeeUsd; // Jupiter swap fee + Solana network fee
    private Instant executedAt;
    private String transactionSignature;
    private TradeReason reason;
    private boolean isDryRun;
    
    public enum TradeType {
        BUY,
        SELL
    }
    
    public enum TradeReason {
        ENTRY_SIGNAL,
        SIGNAL,
        STOP_LOSS,
        TAKE_PROFIT,
        TRAILING_STOP,
        MANUAL,
        MANUAL_EXIT,
        RISK_LIMIT
    }
    
    public Trade(String id, String tokenAddress, String tokenSymbol, TradeType type,
                 double priceUsd, double amountToken, double amountUsd) {
        this.id = id;
        this.tokenAddress = tokenAddress;
        this.tokenSymbol = tokenSymbol;
        this.type = type;
        this.priceUsd = priceUsd;
        this.amountToken = amountToken;
        this.amountUsd = amountUsd;
        this.executedAt = Instant.now();
        this.isDryRun = false;
    }
    
    // Constructor for live trading
    public Trade(String tokenAddress, String tokenSymbol, TradeType type, double priceUsd,
                 double amountToken, double amountUsd, TradeReason reason, String txSignature) {
        this.id = txSignature != null ? txSignature : String.valueOf(System.currentTimeMillis());
        this.tokenAddress = tokenAddress;
        this.tokenSymbol = tokenSymbol;
        this.type = type;
        this.priceUsd = priceUsd;
        this.amountToken = amountToken;
        this.amountUsd = amountUsd;
        this.reason = reason;
        this.transactionSignature = txSignature;
        this.executedAt = Instant.now();
        this.isDryRun = false;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTokenAddress() {
        return tokenAddress;
    }
    
    public void setTokenAddress(String tokenAddress) {
        this.tokenAddress = tokenAddress;
    }
    
    public String getTokenSymbol() {
        return tokenSymbol;
    }
    
    public void setTokenSymbol(String tokenSymbol) {
        this.tokenSymbol = tokenSymbol;
    }
    
    public TradeType getType() {
        return type;
    }
    
    public void setType(TradeType type) {
        this.type = type;
    }
    
    public double getPriceUsd() {
        return priceUsd;
    }
    
    public void setPriceUsd(double priceUsd) {
        this.priceUsd = priceUsd;
    }
    
    public double getAmountToken() {
        return amountToken;
    }
    
    public void setAmountToken(double amountToken) {
        this.amountToken = amountToken;
    }
    
    public double getAmountUsd() {
        return amountUsd;
    }
    
    public void setAmountUsd(double amountUsd) {
        this.amountUsd = amountUsd;
    }
    
    public double getProfitLossUsd() {
        return profitLossUsd;
    }
    
    public void setProfitLossUsd(double profitLossUsd) {
        this.profitLossUsd = profitLossUsd;
    }
    
    public double getProfitLossPercent() {
        return profitLossPercent;
    }
    
    public void setProfitLossPercent(double profitLossPercent) {
        this.profitLossPercent = profitLossPercent;
    }
    
    public double getTransactionFeeUsd() {
        return transactionFeeUsd;
    }
    
    public void setTransactionFeeUsd(double transactionFeeUsd) {
        this.transactionFeeUsd = transactionFeeUsd;
    }
    
    public Instant getExecutedAt() {
        return executedAt;
    }
    
    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
    
    public String getTransactionSignature() {
        return transactionSignature;
    }
    
    public void setTransactionSignature(String transactionSignature) {
        this.transactionSignature = transactionSignature;
    }
    
    public TradeReason getReason() {
        return reason;
    }
    
    public void setReason(TradeReason reason) {
        this.reason = reason;
    }
    
    public boolean isDryRun() {
        return isDryRun;
    }
    
    public void setDryRun(boolean dryRun) {
        isDryRun = dryRun;
    }
    
    // Alias method for compatibility
    public void setPnL(double pnl) {
        this.profitLossUsd = pnl;
    }
    
    @Override
    public String toString() {
        return String.format("Trade{id='%s', type=%s, token=%s, price=$%.8f, amount=$%.2f, P/L=$%.2f (%.2f%%)}",
                id, type, tokenSymbol, priceUsd, amountUsd, profitLossUsd, profitLossPercent);
    }
}
