import { isFinexEnabled } from '../../../api';
import { DEFAULT_TRADING_VIEW_INTERVAL } from '../../../constants';
import { Market, Ticker, TickerEvent } from '../markets';

export const generateSocketURI = (baseUrl: string, s: string[]) => {
    // Format: ws://host/path/?stream=MARKET.trades&stream=MARKET.update&stream=global.tickers
    return `${baseUrl}/?${s.sort().map(stream => `stream=${stream}`).join('&')}`;
};

export const formatTicker = (events: { [pair: string]: TickerEvent }): { [pair: string]: Ticker } => {
    const tickers = {};
    for (const market in events) {
        if (events.hasOwnProperty(market)) {
            const event: TickerEvent = events[market];
            const {
                amount,
                avg_price,
                high,
                last,
                low,
                open,
                price_change_percent,
                volume,
            } = event;
            tickers[market] = {
                amount,
                avg_price,
                high,
                last,
                low,
                open,
                price_change_percent,
                volume,
            };
        }
    }

    return tickers;
};

export const streamsBuilder = (withAuth: boolean, prevSubscriptions: string[], market: Market | undefined) => {
    let streams: string[] = ['global.tickers'];

    // Add market-specific streams if market is available
    if (market) {
        streams = [
            ...streams,
            `${market.id}.trades`,
            `${market.id}.update`,
        ];
    }

    if (withAuth) {
        // Backend expects these authenticated channel names
        streams = [
            ...streams,
            'order',            // Backend channel for private orders
            'trade',            // Backend channel for private trades
            'deposit_address',  // Backend channel for deposit addresses
            'balances',         // Frontend expects 'balances' but backend uses 'funds'
        ];

        if (isFinexEnabled()) {
            streams = [
                ...streams,
                'balances',     // Additional balances for Finex
            ];
        }
    }
    
    for (const stream of prevSubscriptions) {
        if (streams.indexOf(stream) < 0) {
            streams.push(stream);
        }
    }

    return streams;
};

export const periodsMapNumber: { [pair: string]: number } = {
    '1m': 1,
    '5m': 5,
    '15m': 15,
    '30m': 30,
    '1h': 60,
    '2h': 120,
    '4h': 240,
    '6h': 360,
    '12h': 720,
    '1d': 1440,
    '3d': 4320,
    '1w': 10080,
};

export const periodsMapString: { [pair: number]: string } = {
    1: '1m',
    5: '5m',
    15: '15m',
    30: '30m',
    60: '1h',
    120: '2h',
    240: '4h',
    360: '6h',
    720: '12h',
    1440: '1d',
    4320: '3d',
    10080: '1w',
};

export const periodStringToMinutes = (period: string): number => periodsMapNumber[period] || +DEFAULT_TRADING_VIEW_INTERVAL;
export const periodMinutesToString = (period: number): string => periodsMapString[period] || periodsMapString[+DEFAULT_TRADING_VIEW_INTERVAL];

export const marketKlineStreams = (marketId: string, periodString: string) => ({
    channels: [
        `kline_${periodString}`, // Backend channel format for klines
    ],
    productIds: [marketId],      // Backend expects product_ids array
    currencies: []               // Empty currencies for kline subscriptions
});
