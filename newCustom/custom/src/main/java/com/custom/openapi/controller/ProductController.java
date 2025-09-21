package com.custom.openapi.controller;

import com.alibaba.fastjson.JSON;
import com.custom.marketdata.entity.Candle;
import com.custom.marketdata.entity.ProductEntity;
import com.custom.marketdata.entity.Ticker;
import com.custom.marketdata.entity.TradeEntity;
import com.custom.marketdata.manager.TickerManager;
import com.custom.marketdata.orderbook.L2OrderBook;
import com.custom.marketdata.orderbook.OrderBookSnapshotManager;
import com.custom.marketdata.repository.CandleRepository;
import com.custom.marketdata.repository.ProductRepository;
import com.custom.marketdata.repository.TradeRepository;
import com.custom.openapi.model.PagedList;
import com.custom.openapi.model.ProductDto;
import com.custom.openapi.model.TradeDto;
import com.custom.openapi.model.peatio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController()
@RequestMapping("/v2/peatio")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    private final OrderBookSnapshotManager orderBookSnapshotManager;
    private final ProductRepository productRepository;
    private final TradeRepository tradeRepository;
    private final CandleRepository candleRepository;
    private final TickerManager tickerManager;

    @GetMapping("/api/products")
    public List<ProductDto> getProducts() {
        List<ProductEntity> products = productRepository.findAll();
        return products.stream().map(this::productDto).collect(Collectors.toList());
    }

    @GetMapping("/api/products/{productId}/trades")
    public List<TradeDto> getProductTrades(@PathVariable String productId) {
        List<TradeEntity> trades = tradeRepository.findByProductId(productId, 50);
        return trades.stream().map(this::tradeDto).collect(Collectors.toList());
    }

    @GetMapping("/api/products/{productId}/candles")
    public List<List<Object>> getProductCandles(@PathVariable String productId, @RequestParam int granularity,
                                                @RequestParam(defaultValue = "1000") int limit) {
        PagedList<Candle> candlePage = candleRepository.findAll(productId, granularity / 60, 1, limit);

        //[
        //    [ time, low, high, open, close, volume ],
        //    [ 1415398768, 0.32, 4.2, 0.35, 4.2, 12.3 ],
        //]
        List<List<Object>> lines = new ArrayList<>();
        candlePage.getItems().forEach(x -> {
            List<Object> line = new ArrayList<>();
            line.add(x.getTime());
            line.add(x.getLow().stripTrailingZeros());
            line.add(x.getHigh().stripTrailingZeros());
            line.add(x.getOpen().stripTrailingZeros());
            line.add(x.getClose().stripTrailingZeros());
            line.add(x.getVolume().stripTrailingZeros());
            lines.add(line);
        });
        return lines;
    }

    @GetMapping("/api/products/{productId}/book")
    public Object getProductBook(@PathVariable String productId, @RequestParam(defaultValue = "2") int level) {
        return switch (level) {
            case 1 -> orderBookSnapshotManager.getL1OrderBook(productId);
            case 2 -> orderBookSnapshotManager.getL2BatchOrderBook(productId);
            case 3 -> orderBookSnapshotManager.getL3OrderBook(productId);
            default -> null;
        };
    }

    // ========== PEATIO ENDPOINTS ==========

    @GetMapping("/public/markets")
    public List<MarketDto> getPeatioMarkets() {
        List<ProductEntity> products = productRepository.findAll();
        return products.stream().map(this::peatioMarketDto).collect(Collectors.toList());
    }

    @GetMapping("/public/markets/tickers")
    public Map<String, TickerDto> getPeatioMarketTickers() {
        List<ProductEntity> products = productRepository.findAll();
        Map<String, TickerDto> tickers = new HashMap<>();

        for (ProductEntity product : products) {
            Ticker ticker = tickerManager.getTicker(product.getId());
            if (ticker != null) {
                tickers.put(product.getId(), peatioTickerDto(ticker));
            }
        }

        return tickers;
    }

    @GetMapping("/public/currencies")
    public List<CurrencyDto> getPeatioCurrencies() {
        List<ProductEntity> products = productRepository.findAll();
        Map<String, CurrencyDto> currencyMap = new HashMap<>();

        // Extract unique currencies from all products
        for (ProductEntity product : products) {
            // Add base currency
            if (!currencyMap.containsKey(product.getBaseCurrency())) {
                CurrencyDto baseCurrency = createCurrencyDto(product.getBaseCurrency());
                currencyMap.put(product.getBaseCurrency(), baseCurrency);
            }

            // Add quote currency
            if (!currencyMap.containsKey(product.getQuoteCurrency())) {
                CurrencyDto quoteCurrency = createCurrencyDto(product.getQuoteCurrency());
                currencyMap.put(product.getQuoteCurrency(), quoteCurrency);
            }
        }

        return new ArrayList<>(currencyMap.values());
    }

    @GetMapping("/public/markets/{market}/k-line")
    public List<List<Object>> getPeatioKLine(@PathVariable String market,
                                           @RequestParam(defaultValue = "60") int period,
                                           @RequestParam(required = false) Long time_from,
                                           @RequestParam(required = false) Long time_to,
                                           @RequestParam(defaultValue = "30") int limit) {
        // Convert period from minutes to granularity (assuming period is in minutes)
        int granularity = period / 60; // Convert to hours for candle granularity
        PagedList<Candle> candlePage = candleRepository.findAll(market, granularity, 1, limit);

        List<List<Object>> kLines = new ArrayList<>();
        candlePage.getItems().forEach(candle -> {
            List<Object> kLine = new ArrayList<>();
            kLine.add(candle.getTime() * 1000); // Convert to milliseconds
            kLine.add(candle.getOpen().stripTrailingZeros());
            kLine.add(candle.getHigh().stripTrailingZeros());
            kLine.add(candle.getLow().stripTrailingZeros());
            kLine.add(candle.getClose().stripTrailingZeros());
            kLine.add(candle.getVolume().stripTrailingZeros());
            kLines.add(kLine);
        });
        return kLines;
    }

    @GetMapping("/public/markets/{market}/order-book")
    public Map<String, List<List<BigDecimal>>> getPeatioOrderBook(@PathVariable String market,
                                                                 @RequestParam(defaultValue = "20") int asks_limit,
                                                                 @RequestParam(defaultValue = "20") int bids_limit) {
        String normalizedMarket = normalizeMarketId(market);
        Object orderBook = orderBookSnapshotManager.getL2BatchOrderBook(normalizedMarket);
        // Parse and format the order book according to Peatio specification
        return parseOrderBookForPeatio(orderBook, asks_limit, bids_limit);
    }

    @GetMapping("/public/markets/{market}/depth")
    public Map<String, List<List<BigDecimal>>> getPeatioDepth(@PathVariable String market,
                                                              @RequestParam(defaultValue = "20") int asks_limit,
                                                              @RequestParam(defaultValue = "20") int bids_limit) {
        // Same as order-book endpoint, just different URL for compatibility
        String normalizedMarket = normalizeMarketId(market);
        Object orderBook = orderBookSnapshotManager.getL2BatchOrderBook(normalizedMarket);
        return parseOrderBookForPeatio(orderBook, asks_limit, bids_limit);
    }

    @GetMapping("/public/markets/{market}/trades")
    public List<PeatioTradeDto> getPeatioTrades(@PathVariable String market,
                                               @RequestParam(defaultValue = "50") int limit,
                                               @RequestParam(required = false) Long timestamp) {
        List<TradeEntity> trades = tradeRepository.findByProductId(market, limit);
        return trades.stream().map(this::peatioTradeDto).collect(Collectors.toList());
    }

    private MarketDto peatioMarketDto(ProductEntity product) {
        MarketDto dto = new MarketDto();
        dto.setId(product.getId());
        dto.setName(product.getBaseCurrency().toUpperCase() + "/" + product.getQuoteCurrency().toUpperCase());
        dto.setBase_unit(product.getBaseCurrency());
        dto.setQuote_unit(product.getQuoteCurrency());
        dto.setAsk_fee(BigDecimal.valueOf(0.0015));
        dto.setBid_fee(BigDecimal.valueOf(0.0015));
        dto.setMin_price(BigDecimal.ZERO);
        dto.setMax_price(BigDecimal.ZERO);
        dto.setMin_amount(BigDecimal.ZERO);
        dto.setAmount_precision(product.getBaseScale());
        dto.setPrice_precision(product.getQuoteScale());
        dto.setState("enabled");
        return dto;
    }

    private TickerDto peatioTickerDto(Ticker ticker) {
        TickerDto dto = new TickerDto();
        dto.setAmount(ticker.getVolume24h());
        dto.setAvg_price(ticker.getClose24h());
        dto.setHigh(ticker.getHigh24h());
        dto.setLast(ticker.getPrice()); // Use getPrice() instead of getLast()
        dto.setLow(ticker.getLow24h());
        dto.setOpen(ticker.getOpen24h());
        dto.setVolume(ticker.getVolume24h());

        if (ticker.getClose24h() != null && ticker.getOpen24h() != null && !ticker.getOpen24h().equals(BigDecimal.ZERO)) {
            BigDecimal change = ticker.getClose24h().subtract(ticker.getOpen24h());
            BigDecimal percentChange = change.divide(ticker.getOpen24h(), 4, RoundingMode.HALF_UP)
                                           .multiply(BigDecimal.valueOf(100));
            dto.setPrice_change_percent(percentChange.stripTrailingZeros() + "%");
        } else {
            dto.setPrice_change_percent("+0.00%");
        }

        return dto;
    }

    private PeatioTradeDto peatioTradeDto(TradeEntity trade) {
        PeatioTradeDto dto = new PeatioTradeDto();
        dto.setId(trade.getSequence());
        dto.setPrice(trade.getPrice());
        dto.setAmount(trade.getSize());
        dto.setTotal(trade.getPrice().multiply(trade.getSize()));
        dto.setMarket(trade.getProductId());
        dto.setCreated_at(trade.getTime().toInstant().toString());
        dto.setTaker_type(trade.getSide().name().toLowerCase());
        return dto;
    }

    private Map<String, List<List<BigDecimal>>> parseOrderBookForPeatio(Object orderBook, int asksLimit, int bidsLimit) {
        Map<String, List<List<BigDecimal>>> result = new HashMap<>();
        result.put("asks", new ArrayList<>());
        result.put("bids", new ArrayList<>());

        if (orderBook == null) {
            return result;
        }

        try {
            L2OrderBook l2OrderBook;
            if (orderBook instanceof L2OrderBook) {
                l2OrderBook = (L2OrderBook) orderBook;
            } else {
                // If it's a JSON string, parse it
                l2OrderBook = JSON.parseObject(orderBook.toString(), L2OrderBook.class);
            }

            if (l2OrderBook != null) {
                // Convert asks (limit to asksLimit)
                if (l2OrderBook.getAsks() != null) {
                    result.get("asks").addAll(
                        l2OrderBook.getAsks().stream()
                            .limit(asksLimit)
                            .map(this::convertOrderBookEntry)
                            .collect(Collectors.toList())
                    );
                }

                // Convert bids (limit to bidsLimit)
                if (l2OrderBook.getBids() != null) {
                    result.get("bids").addAll(
                        l2OrderBook.getBids().stream()
                            .limit(bidsLimit)
                            .map(this::convertOrderBookEntry)
                            .collect(Collectors.toList())
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing order book: {}", e.getMessage(), e);
        }

        return result;
    }

    private String normalizeMarketId(String market) {
        // Convert from frontend format (btcusdt) to backend format (BTC-USDT)
        if (market == null || market.isEmpty()) {
            return market;
        }
        
        // If already in correct format, return as is
        if (market.contains("-")) {
            return market.toUpperCase();
        }
        
        // Handle common trading pairs
        market = market.toLowerCase();
        if (market.equals("btcusdt")) {
            return "BTC-USDT";
        } else if (market.equals("ethusdt")) {
            return "ETH-USDT";
        }
        
        // Generic conversion: split at known suffixes
        String[] commonSuffixes = {"usdt", "btc", "eth"};
        for (String suffix : commonSuffixes) {
            if (market.endsWith(suffix) && market.length() > suffix.length()) {
                String base = market.substring(0, market.length() - suffix.length());
                return base.toUpperCase() + "-" + suffix.toUpperCase();
            }
        }
        
        // Fallback: return original
        return market.toUpperCase();
    }

    private List<BigDecimal> convertOrderBookEntry(L2OrderBook.Line line) {
        List<BigDecimal> result = new ArrayList<>();
        if (line != null && line.size() >= 2) {
            // [price, size] format
            result.add(new BigDecimal(line.getPrice()));
            result.add(new BigDecimal(line.getSize()));
        }
        return result;
    }

    private ProductDto productDto(ProductEntity product) {
        ProductDto productDto = new ProductDto();
        BeanUtils.copyProperties(product, productDto);
        productDto.setId(product.getId());
        productDto.setQuoteIncrement(String.valueOf(product.getQuoteIncrement()));
        return productDto;
    }

    private TradeDto tradeDto(TradeEntity trade) {
        TradeDto tradeDto = new TradeDto();
        tradeDto.setSequence(trade.getSequence());
        tradeDto.setTime(trade.getTime().toInstant().toString());
        tradeDto.setPrice(trade.getPrice().toPlainString());
        tradeDto.setSize(trade.getSize().toPlainString());
        tradeDto.setSide(trade.getSide().name().toLowerCase());
        return tradeDto;
    }

    private CurrencyDto createCurrencyDto(String currencyCode) {
        CurrencyDto currency = new CurrencyDto();
        currency.setId(currencyCode.toLowerCase());
        currency.setName(currencyCode.toUpperCase());
        currency.setDescription(currencyCode.toUpperCase() + " Currency");
        currency.setHomepage("");
        currency.setPrice(BigDecimal.ZERO);
        currency.setExplorer_transaction("");
        currency.setExplorer_address("");
        currency.setType("coin");
        currency.setDeposit_fee(0);
        currency.setMin_deposit_amount(1);
        currency.setWithdraw_fee(0);
        currency.setMin_withdraw_amount(1);
        currency.setWithdraw_limit_24h(1000000);
        currency.setWithdraw_limit_72h(3000000);
        currency.setBase_factor("1");
        currency.setPrecision(8);
        currency.setIcon_url("");
        currency.setMin_confirmations(1);
        currency.setCode(currencyCode.toLowerCase());
        currency.setBlockchain_key("");
        currency.setDeposit_enabled(true);
        currency.setWithdrawal_enabled(true);
        currency.setVisible(true);
        currency.setPosition(1);
        currency.setSubunits(true);
        currency.setOptions("");
        return currency;
    }
}
