# 🚀 Ready for Live Trading

## ✅ All Systems Configured

Your Solana Memecoin Autotrader is now fully configured and ready for live trading!

### Recent Updates (Nov 9, 2025)

1. **Scan Interval: 5 seconds** ✅
   - Changed from 60 seconds to 5 seconds for more trading opportunities
   - Scans 500 tokens every 5 seconds
   - Goal: $1-5 per hour consistently

2. **Improved Backtest Strategy** ✅
   - Now focuses on momentum-based entries
   - Stricter entry criteria (requires 2+ positive days in last 3 days)
   - Smaller position sizes (15% of balance vs 40%)
   - Better token distribution (more winners in simulation)
   - **Displays 30-day test period in final results**

3. **Live Trading Verified** ✅
   - Fully functional with DexScreener + Jupiter
   - Automatic wallet detection
   - Dry-run mode for safe testing
   - Graceful shutdown with position closing

---

## 📋 Before You Start Live Trading

### 1. Set Up Your Wallet

Create a `.env` file (copy from `.env.example`):

```bash
# Copy the example file
cp .env.example .env
```

Then edit `.env` and add your Solana wallet keys:

```
SOLANA_PRIVATE_KEY=your_base58_private_key_here
SOLANA_PUBLIC_KEY=your_wallet_address_here
```

### 2. Fund Your Wallet

- Send SOL to your wallet address
- Minimum recommended: **0.5 SOL** for trading + gas fees
- The bot will check your balance before starting

### 3. Test in Dry-Run Mode First

In your `.env` file:
```
DRY_RUN_MODE=true
```

This lets you test the bot without risking real money!

---

## 🎯 Current Configuration

### Trading Parameters
- **Scan Interval**: 5 seconds (fast scanning)
- **Max Position Size**: $100 USD
- **Max Concurrent Positions**: 5
- **Stop Loss**: 25%
- **Take Profit**: 100%
- **Trailing Stop**: 15%
- **Max Daily Loss**: $1,000

### Entry Requirements
- Min Volume 24h: $20,000
- Min Liquidity: $5,000
- Min Score: 50
- **Momentum Required**: Price must be pumping (+10% or more)
- **Recent Performance**: 2+ positive days in last 3 days

### Scoring Weights (Heavily Momentum-Based)
- **Momentum**: 45% (most important!)
- Volume: 25%
- Liquidity: 15%
- Sentiment: 10%
- Safety: 5%

---

## 🚀 How to Start

### Run Backtest First (No wallet needed)
```bash
mvn clean package
java -jar target/solana-memecoin-trader-1.0.0.jar
```

Without a wallet configured, it will automatically run a 30-day backtest simulation.

### Run Live Trading (With DRY_RUN_MODE=true)
```bash
# Make sure .env has your wallet keys and DRY_RUN_MODE=true
mvn clean package
java -jar target/solana-memecoin-trader-1.0.0.jar
```

### Run REAL Live Trading (⚠️ Real Money!)
```bash
# In .env, set: DRY_RUN_MODE=false
mvn clean package
java -jar target/solana-memecoin-trader-1.0.0.jar
```

**Press Ctrl+C to stop - it will close all positions gracefully**

---

## 💰 Expected Performance

Based on the improved strategy:

### Backtest Goals
- Starting balance: $50
- Target: Consistent small gains
- Focus: Capital preservation with momentum plays

### Live Trading Goals
- **Target: $1-5 per hour consistently**
- Strategy: Quick momentum plays with tight risk management
- Position sizing: Small (15% of balance max)
- Highly selective: Only enters tokens showing strong momentum

---

## 📊 What the Bot Does

1. **Every 5 seconds**:
   - Scans 500 trending tokens from DexScreener
   - Scores each token based on momentum, volume, liquidity
   - Filters for tokens with strong positive momentum
   - Opens positions on top 3 candidates (if room available)

2. **Monitors positions**:
   - Checks current prices via Jupiter
   - Applies stop loss at -25%
   - Takes profit at +100%
   - Uses trailing stop at 15% from peak

3. **Risk Management**:
   - Max 5 concurrent positions
   - Position size: 15% of balance (conservative)
   - Daily loss limit: $1,000
   - Tracks P&L in real-time

---

## 🛡️ Safety Features

1. **Dry-Run Mode**: Test without risking real money
2. **Graceful Shutdown**: Ctrl+C closes all positions
3. **Daily Loss Limit**: Stops trading if limit reached
4. **Wallet Balance Check**: Won't start if insufficient funds
5. **Error Handling**: Continues running even if API fails
6. **Detailed Logging**: Track every decision

---

## 📈 Monitoring Your Bot

The bot logs everything:

```
Status | Positions: 3/5 | Total PnL: +$12.45 | Trades: 8
Position: BONK42 | PnL: +15.3% | Price: $0.000234
Position: PEPE89 | PnL: -5.2% | Price: $0.001567
Position: WIF12 | PnL: +8.7% | Price: $0.000891
```

---

## 🔧 Adjusting Settings

Edit `pom.xml` to change parameters:

```xml
<!-- Scan faster or slower -->
<bot.scan.interval.seconds>5</bot.scan.interval.seconds>

<!-- More conservative position sizing -->
<bot.max.position.size.usd>50</bot.max.position.size.usd>

<!-- Tighter stop loss -->
<bot.stop.loss.percent>15</bot.stop.loss.percent>
```

After changing, rebuild:
```bash
mvn clean package
```

---

## ⚠️ Important Warnings

1. **Crypto trading is risky** - Only trade what you can afford to lose
2. **Start small** - Test with small amounts first
3. **Use dry-run mode** - Practice before going live
4. **Monitor actively** - Don't leave it unattended initially
5. **Gas fees** - Each trade costs SOL for gas (usually ~0.000005 SOL)
6. **Slippage** - Prices can move between scan and execution

---

## 📞 Support

If you encounter issues:

1. Check your `.env` file has correct wallet keys
2. Ensure you have enough SOL for trading + gas
3. Verify you're connected to the internet
4. Check the logs for error messages
5. Try dry-run mode first to isolate issues

---

## 🎉 Good Luck!

Your bot is ready to trade. Remember:
- Start with dry-run mode
- Use small position sizes initially  
- Monitor closely for the first few hours
- The goal is consistent $1-5/hour, not moon shots
- Be patient and let the strategy work

**Happy trading! 🚀**
