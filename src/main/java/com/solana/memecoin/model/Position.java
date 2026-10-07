package com.solana.memecoin.model;

import java.time.Instant;

/**
 * Represents an active trading position
 */
public class Position {
    private String id;
    private TokenInfo token;
    private double entryPriceUsd;
    private double currentPriceUsd;
    private double amountToken;
    private double amountUsd;
    private Instant openedAt;
    private double stopLossPrice;
    private double takeProfitPrice;
    private double highestPrice;
    private double trailingStopPrice;
    private PositionStatus status;
    private double initialStopLossPercent; // Initial wider stop-loss
    private double finalStopLossPercent; // Tighter stop-loss after grace period
    private static final long STOP_LOSS_GRACE_PERIOD_MINUTES = 5;
    
    public enum PositionStatus {
        OPEN,
        CLOSED,
        STOP_LOSS_TRIGGERED,
        TAKE_PROFIT_TRIGGERED,
        TRAILING_STOP_TRIGGERED
    }
    
    public Position(String id, TokenInfo token, double entryPriceUsd, double amountToken, double amountUsd) {
        this.id = id;
        this.token = token;
        this.entryPriceUsd = entryPriceUsd;
        this.currentPriceUsd = entryPriceUsd;
        this.amountToken = amountToken;
        this.amountUsd = amountUsd;
        this.openedAt = Instant.now();
        this.highestPrice = entryPriceUsd;
        this.status = PositionStatus.OPEN;
        this.initialStopLossPercent = 25.0; // 25% stop-loss for first 5 minutes
        this.finalStopLossPercent = 15.0;   // Tighten to 15% after grace period
    }
    
    // Constructor for live trading (without TokenInfo object)
    public Position(String tokenAddress, String symbol, double entryPrice, double tokenAmount, double positionSizeUsd) {
        this.id = tokenAddress;
        this.token = new TokenInfo(tokenAddress);
        this.token.setSymbol(symbol);
        this.entryPriceUsd = entryPrice;
        this.currentPriceUsd = entryPrice;
        this.amountToken = tokenAmount;
        this.amountUsd = positionSizeUsd;
        this.openedAt = Instant.now();
        this.highestPrice = entryPrice;
        this.status = PositionStatus.OPEN;
        this.initialStopLossPercent = 25.0; // 25% stop-loss for first 5 minutes
        this.finalStopLossPercent = 15.0;   // Tighten to 15% after grace period
    }
    
    /**
     * Calculate dynamic take profit percentage based on time held
     * Starts at 200% and exponentially decays to 0% over 60 minutes
     * At 30 minutes: ~150%
     * At 60 minutes: 0%
     */
    public double getDynamicTakeProfitPercent() {
        long minutesHeld = java.time.Duration.between(openedAt, Instant.now()).toMinutes();
        
        // Exponential decay formula: y = 200 * e^(-k*t)
        // where k is chosen so that at t=60, y=0
        // Using quadratic decay for smoother curve: y = 200 - (200/3600)*t^2
        // This gives us: t=0: 200%, t=30: 150%, t=60: 0%
        
        if (minutesHeld >= 60) {
            return 0.0; // After 1 hour, sell regardless of price
        }
        
        // Quadratic decay: 200 - (200/3600) * t^2
        // At t=30: 200 - (200/3600)*900 = 200 - 50 = 150
        // At t=60: 200 - (200/3600)*3600 = 200 - 200 = 0
        double takeProfitPercent = 200.0 - (200.0 / 3600.0) * (minutesHeld * minutesHeld);
        
        return Math.max(0.0, takeProfitPercent);
    }
    
    public double getProfitLossPercent() {
        return ((currentPriceUsd - entryPriceUsd) / entryPriceUsd) * 100;
    }
    
    public double getProfitLossUsd() {
        return (currentPriceUsd - entryPriceUsd) * amountToken;
    }
    
    public double getCurrentValueUsd() {
        return currentPriceUsd * amountToken;
    }
    
    public void updateCurrentPrice(double price) {
        this.currentPriceUsd = price;
        if (price > highestPrice) {
            this.highestPrice = price;
            updateTrailingStop();
        }
    }
    
    // Aliases for compatibility
    public String getTokenAddress() {
        return id;
    }
    
    public String getSymbol() {
        return token != null ? token.getSymbol() : "UNKNOWN";
    }
    
    public double getEntryPrice() {
        return entryPriceUsd;
    }
    
    public double getCurrentPrice() {
        return currentPriceUsd;
    }
    
    public double getTokenAmount() {
        return amountToken;
    }
    
    public double getPositionSizeUsd() {
        return amountUsd;
    }
    
    public double getPnLPercent() {
        return getProfitLossPercent();
    }
    
    public void updatePrice(double price) {
        updateCurrentPrice(price);
    }
    
    private void updateTrailingStop() {
        // Update trailing stop based on highest price
        if (trailingStopPrice > 0) {
            double trailingPercent = ((highestPrice - trailingStopPrice) / trailingStopPrice) * 100;
            if (trailingPercent > 0) {
                // Trail the stop loss
                double newTrailingStop = highestPrice * (1 - (trailingPercent / 100));
                if (newTrailingStop > trailingStopPrice) {
                    trailingStopPrice = newTrailingStop;
                }
            }
        }
    }
    
    /**
     * Dynamic stop-loss that starts wider (25%) and tightens after 5 minutes (15%)
     */
    public boolean shouldStopLoss() {
        if (stopLossPrice <= 0) return false;
        
        // Calculate time held
        long minutesHeld = java.time.Duration.between(openedAt, Instant.now()).toMinutes();
        
        // Use wider stop-loss for first 5 minutes to avoid false stops
        double activeStopLossPercent = minutesHeld < STOP_LOSS_GRACE_PERIOD_MINUTES 
            ? initialStopLossPercent 
            : finalStopLossPercent;
        
        // Calculate dynamic stop-loss price
        double dynamicStopLossPrice = entryPriceUsd * (1 - activeStopLossPercent / 100);
        
        return currentPriceUsd <= dynamicStopLossPrice;
    }
    
    /**
     * Get current active stop-loss percentage based on time held
     */
    public double getActiveStopLossPercent() {
        long minutesHeld = java.time.Duration.between(openedAt, Instant.now()).toMinutes();
        return minutesHeld < STOP_LOSS_GRACE_PERIOD_MINUTES 
            ? initialStopLossPercent 
            : finalStopLossPercent;
    }
    
    public boolean shouldTakeProfit() {
        return takeProfitPrice > 0 && currentPriceUsd >= takeProfitPrice;
    }
    
    public boolean shouldTrailingStop() {
        return trailingStopPrice > 0 && currentPriceUsd <= trailingStopPrice;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public TokenInfo getToken() {
        return token;
    }
    
    public double getEntryPriceUsd() {
        return entryPriceUsd;
    }
    
    public double getCurrentPriceUsd() {
        return currentPriceUsd;
    }
    
    public double getAmountToken() {
        return amountToken;
    }
    
    public double getAmountUsd() {
        return amountUsd;
    }
    
    public Instant getOpenedAt() {
        return openedAt;
    }
    
    public double getStopLossPrice() {
        return stopLossPrice;
    }
    
    public void setStopLossPrice(double stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }
    
    public double getTakeProfitPrice() {
        return takeProfitPrice;
    }
    
    public void setTakeProfitPrice(double takeProfitPrice) {
        this.takeProfitPrice = takeProfitPrice;
    }
    
    public double getHighestPrice() {
        return highestPrice;
    }
    
    public double getTrailingStopPrice() {
        return trailingStopPrice;
    }
    
    public void setTrailingStopPrice(double trailingStopPrice) {
        this.trailingStopPrice = trailingStopPrice;
    }
    
    public PositionStatus getStatus() {
        return status;
    }
    
    public void setStatus(PositionStatus status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return String.format("Position{id='%s', token=%s, entry=$%.8f, current=$%.8f, P/L=%.2f%%}",
                id, token.getSymbol(), entryPriceUsd, currentPriceUsd, getProfitLossPercent());
    }
}
