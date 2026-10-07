# P&L Tracker Implementation Summary

## Overview
Added comprehensive session P&L (Profit & Loss) tracking to the live trading engine. The tracker monitors every trade and provides detailed statistics when the program is stopped.

## Key Features Implemented

### 1. Session P&L Tracking
- **Session P&L Variable**: Renamed `totalPnL` to `sessionPnL` for clarity
- **Initialization**: Starts at $0.00 every time the program runs
- **Real-time Updates**: Updated on every sell trade (both profits and losses)
- **Persistent Display**: Shows running total on every sell trade log

### 2. Live Trade Logging Enhancement
Each sell trade now displays:
```
💰 SELL TOKEN | Reason: TAKE_PROFIT | Entry: $0.000123 | Exit: $0.000145 | PnL: +$5.23 (+18.5%) | Session P&L: +$12.45 | Time: 2025-11-11 11:07:00
```

Key information:
- Individual trade P&L (in USD and %)
- Running session P&L
- Trade reason (TAKE_PROFIT, STOP_LOSS, TRAILING_STOP, MANUAL_EXIT)
- Entry and exit prices
- Timestamp

### 3. Comprehensive Session Summary on Shutdown
When the program is stopped (Ctrl+C), it displays:

```
╔═══════════════════════════════════════════════╗
║          SESSION TRADING SUMMARY             ║
╚═══════════════════════════════════════════════╝

⏱️  Session Duration: 2h 15m

📊 TRADING PERFORMANCE:
  • Total Trades (Sells): 15
  • Winning Trades: 9 (60.0%)
  • Losing Trades: 6

💵 PROFIT & LOSS:
  • Total Profit: $125.50
  • Total Loss: -$45.30
  • Largest Win: $25.00
  • Largest Loss: -$15.00

💰 SESSION NET P&L: 🟢 +$80.20

════════════════════════════════════════════════
✅ Profitable session! Great work!
════════════════════════════════════════════════
```

### 4. Statistics Tracked
- **Session Duration**: Total time the bot was running
- **Total Trades**: Number of completed sell transactions
- **Winning Trades**: Trades that resulted in profit
- **Losing Trades**: Trades that resulted in loss
- **Win Rate**: Percentage of profitable trades
- **Total Profit**: Sum of all profitable trades
- **Total Loss**: Sum of all losing trades
- **Largest Win**: Biggest single winning trade
- **Largest Loss**: Biggest single losing trade
- **Session Net P&L**: Final profit/loss for the session

### 5. Automatic Position Closure on Shutdown
- When the user presses Ctrl+C:
  1. All open positions are automatically closed
  2. Each closure is logged to the session P&L
  3. Final summary is displayed with complete statistics

## Implementation Details

### Files Modified
- `src/main/java/com/solana/memecoin/trading/LiveTradingEngine.java`

### Key Changes
1. Added `sessionStartTime` field to track when the trading session began
2. Renamed `totalPnL` to `sessionPnL` for clarity
3. Updated `closePosition()` to add P&L to `sessionPnL`
4. Enhanced sell trade logging to include running session P&L
5. Created `displaySessionSummary()` method with comprehensive statistics
6. Updated `stop()` method to:
   - Display shutdown header
   - Close all open positions
   - Calculate and display final session summary

### Behavior
- **Dry Run Mode**: Works the same way - tracks simulated trades
- **Live Trading Mode**: Tracks real trades with actual USD values
- **Reset**: Session P&L resets to $0 every time the program is restarted
- **Accumulation**: All sell trades (profit or loss) accumulate to the session total

## Usage

### Starting the Bot
```bash
mvn clean package
java -jar target/solana-memecoin-trader-1.0.0.jar
```

### Stopping the Bot
Press `Ctrl+C` to trigger graceful shutdown:
1. Bot closes all open positions
2. Displays comprehensive session summary
3. Shows final P&L

## Example Output

### Opening Positions
```
✅ BUY PEPE | Price: $0.000150 | Amount: 133333.33 | Value: $20.00 | Vol: $500000 | Liq: $100000 | Scanned: 500 tokens | Time: 2025-11-11 11:00:00
```

### Closing Positions
```
💰 SELL PEPE | Reason: TAKE_PROFIT | Entry: $0.000150 | Exit: $0.000180 | PnL: +$4.00 (+20.0%) | Session P&L: +$4.00 | Time: 2025-11-11 11:15:00
```

### Session Summary
Displayed automatically when program stops - shows complete trading statistics for the session.

## Benefits
1. **Transparency**: See exactly how much profit/loss accumulated during the session
2. **Performance Tracking**: Understand win rate and trading effectiveness
3. **Risk Management**: Monitor losses in real-time
4. **Historical Context**: Session duration provides context for performance
5. **Easy Monitoring**: Running session P&L on every sell trade log
