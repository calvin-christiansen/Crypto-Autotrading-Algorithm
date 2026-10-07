# Solana Memecoin Autotrader

An autonomous Java-based cryptocurrency trading bot designed to scan, analyze, and trade Solana memecoins using algorithmic strategies.

## 📖 Overview

The Solana Memecoin Autotrader is an intelligent trading system that automatically identifies profitable trading opportunities in the volatile memecoin market on the Solana blockchain. It uses multi-factor analysis to score tokens based on momentum, volume, liquidity, and safety metrics, then executes trades through the Jupiter aggregator for optimal pricing.

**Key Capabilities:**
- **Automated Token Discovery**: Scans 500+ trending Solana tokens via DexScreener API
- **Smart Scoring System**: Evaluates tokens using 5 weighted factors (momentum, volume, liquidity, sentiment, safety)
- **Risk Management**: Implements stop-loss, take-profit, and trailing-stop mechanisms
- **Fee-Aware Strategy**: Optimized position sizing to overcome transaction fees ($50+ positions)
- **Market Adaptation**: Dynamically adjusts strategy based on bull/bear/sideways market conditions
- **Backtesting Mode**: Test strategies with historical data before risking real funds

**Perfect for**: Traders looking to automate memecoin trading, developers wanting to build algorithmic trading systems, or anyone interested in crypto trading bots.

## Features

- 🔄 **Automatic Backtesting**: Runs simulated trading when no wallet keys are configured
- 📊 **Smart Token Scoring**: Multi-factor analysis (momentum, volume, liquidity, sentiment, safety)
- 🛡️ **Risk Management**: Stop-loss, take-profit, and trailing stop mechanisms
- 💰 **Position Management**: Handles up to 5 concurrent positions with proper sizing
- 📈 **Performance Tracking**: Detailed trade history and P&L calculations

## Backtesting Mode

When you leave the Solana wallet keys blank in the `.env` file, the bot automatically runs in **backtest mode**:

- Starts with $50 virtual balance
- Simulates trading with 100 days of historical memecoin data
- Runs until balance reaches $0 (depleted) or $100 (target reached)
- Uses the same strategy and risk parameters as live trading
- Shows detailed results including win rate, P&L, and trade statistics

## Prerequisites

- **Java 21** or higher
- **Maven 3.6+** (for building and running)
- (Optional) Solana wallet with private key for live trading

### Installing Prerequisites

#### macOS
```bash
# Install Java 21 (if not installed)
brew install openjdk@21

# Install Maven
brew install maven

# Verify installations
java -version
mvn -version
```

#### Linux (Ubuntu/Debian)
```bash
# Install Java 21
sudo apt update
sudo apt install openjdk-21-jdk

# Install Maven
sudo apt install maven

# Verify installations
java -version
mvn -version
```

#### Windows
1. Download and install [Java 21 JDK](https://www.oracle.com/java/technologies/downloads/#java21)
2. Download and install [Maven](https://maven.apache.org/download.cgi)
3. Add both to your system PATH
4. Verify installations:
```cmd
java -version
mvn -version
```

## Quick Start

### 1. Clone or Download the Project

```bash
cd /path/to/Solana-Memecoin-Autotrader
```

### 2. Set Up Environment (Optional - for backtesting, skip this)

For **backtesting mode**, you don't need to create a `.env` file. Just run the program directly.

For **live trading mode** (not yet implemented):
```bash
# Copy the example environment file
cp .env.example .env

# Edit .env and add your wallet keys
# SOLANA_PRIVATE_KEY=your_private_key_here
# SOLANA_PUBLIC_KEY=your_public_key_here
```

### 3. Build the Project

```bash
mvn clean package
```

This will:
- Download all dependencies
- Compile the source code
- Create an executable JAR file in `target/`

### 4. Run the Bot

#### Option A: Run with Maven (Recommended)
```bash
mvn exec:java -Dexec.mainClass="com.solana.memecoin.MemecoinTraderBot"
```

cd Downloads/Calvin/Coding/Java/Solana-Memecoin-Autotrader

#### Option B: Run the JAR directly
```bash
java -jar target/solana-memecoin-trader-1.0.0.jar
```

#### Option C: Quick run without building
```bash
mvn compile exec:java -Dexec.mainClass="com.solana.memecoin.MemecoinTraderBot"
```

## Understanding the Output

### Backtesting Mode Output

When running in backtest mode, you'll see:

1. **Bot Header**: Version and mode information
2. **Daily Updates**: Token evaluations and position changes
3. **Trade Notifications**: 
   - ✓ Position opened with score and size
   - 📈 Position closed with profit
   - 📉 Position closed with loss
4. **Final Results**: 
   - Starting vs Final balance
   - Total P&L and percentage
   - Win/Loss statistics
   - Win rate

Example output:
```
=== Starting Backtest ===
Starting Balance: $50.0
Target Balance: $100.0
========================

--- Day 1 ---
Evaluating Token: PEPE1
✓ OPENED Position: PEPE1 at $0.0023 | Score: 72.5 | Size: $10.0

--- Day 2 ---
📈 CLOSED Position: PEPE1 at $0.0034 | Reason: Take Profit Hit | P&L: $4.78 (47.83%)

...

╔════════════════════════════════════════╗
║       BACKTEST FINAL RESULTS          ║
╠════════════════════════════════════════╣
║ Starting Balance: $50.00             ║
║ Final Balance:    $87.35             ║
║ Total P&L:        $37.35 (74.7%)     ║
╠════════════════════════════════════════╣
║ Total Trades:     23                  ║
║ Winning Trades:   14                  ║
║ Losing Trades:    9                   ║
║ Win Rate:         60.9%               ║
╚════════════════════════════════════════╝
```

## Configuration

The bot's behavior is configured in `pom.xml` properties:

### Scan Configuration
- `bot.scan.interval.seconds`: How often to scan for tokens (default: 60)
- `bot.min.volume.24h`: Minimum 24h volume required (default: $50,000)
- `bot.min.liquidity.usd`: Minimum liquidity required (default: $10,000)
- `bot.min.holders`: Minimum number of holders (default: 50)
- `bot.min.final.score`: Minimum score to enter trade (default: 65)

### Risk Parameters
- `bot.max.position.size.usd`: Maximum size per position (default: $100)
- `bot.stop.loss.percent`: Stop loss percentage (default: 15%)
- `bot.take.profit.percent`: Take profit percentage (default: 50%)
- `bot.trailing.stop.percent`: Trailing stop percentage (default: 10%)
- `bot.max.concurrent.positions`: Max simultaneous positions (default: 5)

### Scoring Weights
- `bot.weight.momentum`: Weight for momentum score (default: 0.30)
- `bot.weight.volume`: Weight for volume score (default: 0.25)
- `bot.weight.liquidity`: Weight for liquidity score (default: 0.20)
- `bot.weight.sentiment`: Weight for sentiment score (default: 0.15)
- `bot.weight.safety`: Weight for safety score (default: 0.10)

## Project Structure

```
src/main/java/com/solana/memecoin/
├── MemecoinTraderBot.java          # Main entry point
├── api/
│   ├── DexScreenerClient.java      # DEX API client
│   └── JupiterClient.java          # Jupiter swap client
├── backtest/
│   └── BacktestEngine.java         # Backtesting engine
├── config/
│   └── BotConfig.java              # Configuration management
└── model/
    ├── Position.java               # Trading position model
    ├── TokenInfo.java              # Token information model
    └── Trade.java                  # Trade record model
```

## Troubleshooting

### "command not found: mvn"
Maven is not installed. Follow the Prerequisites section above to install it.

### "command not found: java"
Java is not installed or not in your PATH. Install Java 21 as described in Prerequisites.

### Build errors
Try cleaning the build:
```bash
mvn clean
mvn compile
```

### The bot immediately exits
Check the console output for error messages. Common issues:
- Missing `.env` file for live trading mode
- Invalid configuration in `pom.xml`
- Compilation errors

## Live Trading (Coming Soon)

Live trading functionality is currently under development. Features planned:
- Real-time token scanning via DexScreener API
- Automated trade execution via Jupiter
- Telegram notifications
- Performance monitoring dashboard
- Risk management overrides

To use live trading when available:
1. Create `.env` file from `.env.example`
2. Add your Solana wallet private and public keys
3. Configure API keys for better rate limits (optional)
4. Set `TRADING_ENABLED=true` and `DRY_RUN_MODE=false`
5. Run the bot

## Safety & Disclaimer

⚠️ **IMPORTANT DISCLAIMERS**:

- This is experimental software for educational purposes
- Cryptocurrency trading carries significant financial risk
- Never invest more than you can afford to lose
- Always test thoroughly in backtest/dry-run mode first
- The developers are not responsible for any financial losses
- Use at your own risk

## License

MIT License - See LICENSE file for details

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

## Support

For issues, questions, or feature requests, please open an issue on GitHub.
