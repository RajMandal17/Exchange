import { incrementalOrderBook } from '../../../api';
import { CommonError, OrderEvent } from '../../types';
import { Market } from '../markets';
import {
    RANGER_CONNECT_DATA,
    RANGER_CONNECT_ERROR,
    RANGER_CONNECT_FETCH,
    RANGER_DIRECT_WRITE,
    RANGER_DISCONNECT_DATA,
    RANGER_DISCONNECT_FETCH,
    RANGER_SUBSCRIPTIONS_DATA,
    RANGER_USER_ORDER_UPDATE,
} from './constants';
import { marketKlineStreams } from './helpers';

export interface RangerConnectFetch {
    type: typeof RANGER_CONNECT_FETCH;
    payload: {
        withAuth: boolean;
    };
}

export interface RangerConnectData {
    type: typeof RANGER_CONNECT_DATA;
}

export interface RangerDisconnectFetch {
    type: typeof RANGER_DISCONNECT_FETCH;
}

export interface RangerDisconnectData {
    type: typeof RANGER_DISCONNECT_DATA;
}

export interface RangerSubscribe {
    type: typeof RANGER_DIRECT_WRITE;
    payload: {
        channels: string[];
        productIds: string[];
        currencies?: string[];  // Optional for funds/balances subscriptions
        token?: string;
    };
}

export interface RangerDirectMessage {
    type: typeof RANGER_DIRECT_WRITE;
    // tslint:disable-next-line no-any
    payload: { [pair: string]: any };
}

export interface RangerConnectError {
    type: typeof RANGER_CONNECT_ERROR;
    payload?: {
        code: number;
        message: string[];
    };
}

export interface SubscriptionsUpdate {
    type: typeof RANGER_SUBSCRIPTIONS_DATA;
    payload: {
        subscriptions: string[];
    };
}

export interface UserOrderUpdate {
    type: typeof RANGER_USER_ORDER_UPDATE;
    payload: OrderEvent;
}

export type RangerAction = RangerConnectFetch |
    RangerConnectData |
    RangerConnectError |
    RangerDisconnectData |
    SubscriptionsUpdate;

export type RangerErrorType = typeof RANGER_CONNECT_ERROR;

export const rangerConnectFetch = (payload: RangerConnectFetch['payload']): RangerConnectFetch => ({
    type: RANGER_CONNECT_FETCH,
    payload,
});

export const rangerConnectData = (): RangerConnectData => ({
    type: RANGER_CONNECT_DATA,
});

export const rangerConnectError = (payload: CommonError): RangerConnectError => ({
    type: RANGER_CONNECT_ERROR,
    payload,
});

export const rangerDisconnectData = (): RangerDisconnectData => ({
    type: RANGER_DISCONNECT_DATA,
});

export const rangerDirectMessage = (payload: RangerDirectMessage['payload']): RangerDirectMessage => ({
    type: RANGER_DIRECT_WRITE,
    payload,
});

export const rangerSubscribe = (payload: RangerSubscribe['payload']): RangerDirectMessage => ({
    type: RANGER_DIRECT_WRITE,
    payload: { 
        type: 'subscribe',           // Backend expects 'type' not 'event'
        productIds: payload.productIds,  // Backend expects 'productIds' (camelCase, not snake_case)
        channels: payload.channels,       // Backend expects 'channels' array
        currencyIds: payload.currencies,  // Backend expects 'currencyIds' (not 'currencies')
        token: payload.token || ''        // Backend expects 'token' field
    },
});

export const rangerUnsubscribe = (payload: RangerSubscribe['payload']): RangerDirectMessage => ({
    type: RANGER_DIRECT_WRITE,
    payload: { 
        type: 'unsubscribe',         // Backend expects 'type' not 'event'
        productIds: payload.productIds,  // Backend expects 'productIds' (camelCase, not snake_case)
        channels: payload.channels,       // Backend expects 'channels' array
        currencyIds: payload.currencies,  // Backend expects 'currencyIds' (not 'currencies')
        token: payload.token || ''        // Backend expects 'token' field
    },
});

export const rangerUserOrderUpdate = (payload: UserOrderUpdate['payload']): UserOrderUpdate => ({
    type: RANGER_USER_ORDER_UPDATE,
    payload,
});

export const marketStreams = (market: Market) => {
    if (incrementalOrderBook()) {
        return {
            channels: ['level2', 'match', 'ticker', 'full'], // Backend expects these channel names including 'full'
            productIds: [market.id],                         // Backend expects product_ids array
            currencies: []                                   // Empty currencies for market subscriptions
        };
    }

    return {
        channels: ['level2', 'match', 'ticker', 'full'],    // Backend expects these channel names including 'full'
        productIds: [market.id],                            // Backend expects product_ids array
        currencies: []                                      // Empty currencies for market subscriptions
    };
};

export const subscriptionsUpdate = (payload: SubscriptionsUpdate['payload']): SubscriptionsUpdate => ({
    type: RANGER_SUBSCRIPTIONS_DATA,
    payload,
});

// Public market subscriptions
export const rangerSubscribeMarket = (market: Market): RangerDirectMessage => rangerSubscribe(marketStreams(market));
export const rangerUnsubscribeMarket = (market: Market): RangerDirectMessage => rangerUnsubscribe(marketStreams(market));

// Authenticated user subscriptions  
export const rangerSubscribeUserChannels = (currencies: string[] = []): RangerDirectMessage => rangerSubscribe({
    channels: ['order', 'trade'],  // Backend expects these for authenticated users
    productIds: [],                // Empty for user-specific channels
    currencies: currencies,        // For funds/balances
    token: ''                      // Will be set by auth system
});

export const rangerUnsubscribeUserChannels = (currencies: string[] = []): RangerDirectMessage => rangerUnsubscribe({
    channels: ['order', 'trade'],  // Backend expects these for authenticated users  
    productIds: [],                // Empty for user-specific channels
    currencies: currencies,        // For funds/balances
    token: ''                      // Will be set by auth system
});

// Kline subscriptions
export const rangerSubscribeKlineMarket = (marketId: string, periodString: string): RangerDirectMessage => rangerSubscribe(marketKlineStreams(marketId, periodString));
export const rangerUnsubscribeKlineMarket = (marketId: string, periodString: string): RangerDirectMessage => rangerUnsubscribe(marketKlineStreams(marketId, periodString));

export const rangerDisconnectFetch = (): RangerDisconnectFetch => ({
    type: RANGER_DISCONNECT_FETCH,
});

