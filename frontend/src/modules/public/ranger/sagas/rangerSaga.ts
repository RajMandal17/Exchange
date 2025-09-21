import { Channel, eventChannel, EventChannel } from 'redux-saga';
import { all, call, cancel, delay, fork, put, race, select, take, takeEvery } from 'redux-saga/effects';
import { isFinexEnabled, rangerUrl } from '../../../../api';
import { store } from '../../../../store';
import { pushHistoryEmit } from '../../../user/history';
import { selectOpenOrdersList, userOpenOrdersUpdate } from '../../../user/openOrders';
import { userOrdersHistoryRangerData} from '../../../user/ordersHistory';
import { updateWalletsDataByRanger, walletsAddressDataWS } from '../../../user/wallets';
import { alertPush } from '../../alert';
import { klinePush } from '../../kline';
import { Market, marketsTickersData, selectCurrentMarket, SetCurrentMarket } from '../../markets';
import { MARKETS_SET_CURRENT_MARKET, MARKETS_SET_CURRENT_MARKET_IFUNSET } from '../../markets/constants';
import { depthData, depthDataIncrement, depthDataSnapshot, selectOrderBookSequence } from '../../orderBook';
import { recentTradesPush } from '../../recentTrades';
import {
    RangerConnectFetch,
    rangerDisconnectData,
    rangerDisconnectFetch,
    rangerSubscribeMarket,
    rangerUnsubscribeMarket,
    rangerUserOrderUpdate,
    subscriptionsUpdate,
    UserOrderUpdate,
} from '../actions';
import {
    RANGER_CONNECT_DATA,
    RANGER_CONNECT_FETCH,
    RANGER_DIRECT_WRITE,
    RANGER_DISCONNECT_DATA,
    RANGER_DISCONNECT_FETCH,
    RANGER_USER_ORDER_UPDATE,
} from '../constants';
import { formatTicker, generateSocketURI, streamsBuilder } from '../helpers';
import { selectSubscriptions } from '../selectors';

interface RangerBuffer {
    messages: object[];
}

// WebSocket message buffering variables
let writeQueue: any[] = [];
let socketReady = false;
let currentSocket: WebSocket | null = null;

const initRanger = (
    { withAuth }: RangerConnectFetch['payload'],
    market: Market | undefined,
    prevSubs: string[],
    buffer: RangerBuffer,
): [EventChannel<any>, WebSocket] => {
    const baseUrl = `${rangerUrl()}/${withAuth ? 'private' : 'public'}`;
    const streams = streamsBuilder(withAuth, prevSubs, market);
    
    console.debug('🔌 Attempting WebSocket connection:', {
        baseUrl,
        streams,
        market: market ? market.id : 'none',
        withAuth
    });

    const ws = new WebSocket(generateSocketURI(baseUrl, streams));
    currentSocket = ws; // Store reference to current socket
    
    const channel = eventChannel(emitter => {
        ws.onopen = () => {
            console.debug('✅ WebSocket connection opened successfully');
            socketReady = true; // Mark socket as ready
            emitter({ type: RANGER_CONNECT_DATA });

            // Send initial subscription for ticker data
            const tickerSubscription = {
                type: 'subscribe',
                product_ids: market ? [market.id] : [], // Subscribe to current market if available
                channels: ['ticker'],
                currencies: [],
                token: ''
            };
            
            console.debug('📡 Sending ticker subscription:', tickerSubscription);
            
            try {
                ws.send(JSON.stringify(tickerSubscription));
                console.debug('✅ Ticker subscription sent successfully');
            } catch (error) {
                console.error('❌ Failed to send ticker subscription:', error);
            }

            // Also send a global tickers subscription to ensure we get all ticker data
            const globalTickerSub = {
                type: 'subscribe',
                channels: ['global.tickers']
            };
            
            console.debug('📡 Sending global ticker subscription:', globalTickerSub);
            
            try {
                ws.send(JSON.stringify(globalTickerSub));
                console.debug('✅ Global ticker subscription sent successfully');
            } catch (error) {
                console.error('❌ Failed to send global ticker subscription:', error);
            }

            // Flush queued messages first
            while (writeQueue.length > 0) {
                const msg = writeQueue.shift();
                try {
                    ws.send(JSON.stringify(msg));
                } catch (error) {
                    console.error('❌ Failed to send queued message:', error);
                }
            }

            // Then send any buffered messages from old system
            while (buffer.messages.length > 0) {
                const message = buffer.messages.shift();
                try {
                    ws.send(JSON.stringify(message));
                } catch (error) {
                    console.error('❌ Failed to send buffered message:', error);
                }
            }
        };
        ws.onerror = error => {
            console.error('❌ WebSocket error:', error);
            socketReady = false; // Mark socket as not ready
        };
        ws.onclose = event => {
            console.debug('🔌 WebSocket connection closed:', event);
            socketReady = false; // Mark socket as not ready
            currentSocket = null; // Clear socket reference
            writeQueue = []; // Clear message queue
            channel.close();
        };
        ws.onmessage = ({ data }) => {
            // tslint:disable-next-line no-any
            let payload: { [pair: string]: any } = {};

            console.debug('📨 Raw WebSocket message received:', data);

            try {
                payload = JSON.parse(data as string);
                console.debug('📦 Parsed WebSocket payload:', payload);
            } catch (e) {
                console.error('❌ Failed to parse WebSocket message:', e, 'Raw data:', data);
                return;
            }

            // Check if this is a direct ticker message (not wrapped in routing key)
            if (payload.type === 'ticker' && payload.productId) {
                console.debug('📊 Direct ticker message received:', payload);
                
                try {
                    // Convert individual ticker to global.tickers format with proper Ticker type
                    const tickerData = {
                        [payload.productId]: {
                            last: String(payload.price || payload.close24h || '0'),
                            high: String(payload.high24h || '0'),
                            low: String(payload.low24h || '0'),
                            volume: String(payload.volume24h || '0'),
                            amount: String(payload.volume24h || '0'),
                            avg_price: String(payload.price || '0'),
                            open: String(payload.open24h || '0'),
                            price_change_percent: String(payload.priceChangePercent || '+0.00%')
                        }
                    };
                    
                    console.debug('📊 Converted direct ticker data:', tickerData);
                    emitter(marketsTickersData(tickerData));
                } catch (err) {
                    console.error('❌ Error processing direct ticker:', err);
                }
                return;
            }

            for (const routingKey in payload) {
                if (payload.hasOwnProperty(routingKey)) {
                    const event = payload[routingKey];
                    console.debug('🔀 Processing routing key:', routingKey, 'with event:', event);

                    console.debug('🔍 Processing routing key:', routingKey, 'with event:', event);

                    const currentMarket = selectCurrentMarket(store.getState());
                    const orderBookMatch = routingKey.match(/([^.]*)\.update/);
                    const orderBookMatchSnap = routingKey.match(/([^.]*)\.ob-snap/);
                    const orderBookMatchInc = routingKey.match(/([^.]*)\.ob-inc/);

                    // public
                    if (orderBookMatch) {
                        // console.log('📈 Order book update for market:', orderBookMatch[1]);
                        // console.log('📊 Current market:', currentMarket && currentMarket.id);
                        if (currentMarket && orderBookMatch[1] === currentMarket.id) {
                            emitter(depthData(event));
                        }

                        return;
                    }

                    // public
                    if (orderBookMatchSnap) {
                        if (currentMarket && orderBookMatchSnap[1] === currentMarket.id) {
                            emitter(depthDataSnapshot(event));
                        }

                        return;
                    }

                    // public
                    if (orderBookMatchInc) {
                        if (currentMarket && orderBookMatchInc[1] === currentMarket.id) {
                            const previousSequence = selectOrderBookSequence(store.getState());
                            if (previousSequence === null) {
                                return;
                            }
                            if (previousSequence + 1 !== event.sequence) {
                                emitter(rangerDisconnectFetch());

                                return;
                            }
                            emitter(depthDataIncrement(event));
                        }

                        return;
                    }

                    // public
                    const klineMatch = String(routingKey).match(/([^.]*)\.kline-(.+)/);
                    if (klineMatch) {
                        emitter(
                            klinePush({
                                marketId: klineMatch[1],
                                kline: event,
                                period: klineMatch[2],
                            }),
                        );

                        return;
                    }

                    // public
                    const tradesMatch = String(routingKey).match(/([^.]*)\.trades/);
                    if (tradesMatch) {
                        emitter(
                            recentTradesPush({
                                trades: event.trades,
                                market: tradesMatch[1],
                            }),
                        );

                        return;
                    }

                    // Check if this is an individual ticker message before the switch
                    if (event && event.type === 'ticker' && event.productId) {
                        try {
                            console.debug('📊 Received individual ticker event:', event);
                            
                            // Convert individual ticker to global.tickers format with proper Ticker type
                            const tickerData = {
                                [event.productId]: {
                                    last: String(event.price || event.close24h || '0'),
                                    high: String(event.high24h || '0'),
                                    low: String(event.low24h || '0'),
                                    volume: String(event.volume24h || '0'),
                                    amount: String(event.volume24h || '0'),
                                    avg_price: String(event.price || '0'),
                                    open: String(event.open24h || '0'),
                                    price_change_percent: String(event.priceChangePercent || '+0.00%')
                                }
                            };
                            
                            console.debug('📊 Converted ticker data:', tickerData);
                            emitter(marketsTickersData(tickerData));
                        } catch (err) {
                            console.error('❌ Error processing individual ticker:', err);
                        }
                        return;
                    }

                    switch (routingKey) {
                        // public
                        case 'global.tickers':
                            try {
                                console.debug('🌐 Received global.tickers event (raw):', event);
                                const formatted = formatTicker(event);

                                // Replace zero/empty last prices with previous last value from store
                                const state = store.getState();
                                const prevTickers =
                                    (state && (state.public.markets || (state.public && state.public.markets)) && ((state.public.markets && state.public.markets.tickers) || (state.public && state.public.markets && state.public.markets.tickers))) ||
                                    {};

                                for (const marketId in formatted) {
                                    if (!formatted.hasOwnProperty(marketId)) {
                                        continue;
                                    }
                                    const lastStr = formatted[marketId] && formatted[marketId].last;
                                    const lastNum = Number(lastStr);
                                    if (!lastStr || isNaN(lastNum) || lastNum === 0) {
                                        const prev = prevTickers && prevTickers[marketId];
                                        if (prev && prev.last) {
                                            formatted[marketId].last = prev.last;
                                        }
                                    }
                                }

                                console.debug('🌐 global.tickers formatted (after zero-fill):', formatted);
                                emitter(marketsTickersData(formatted));
                            } catch (err) {
                                // keep only ticker errors silent in console
                            }

                            return;
                        // public
                        case 'success':
                            switch (event.message) {
                                case 'subscribed':
                                case 'unsubscribed':
                                    emitter(subscriptionsUpdate({ subscriptions: event.streams }));

                                    return;
                                default:
                            }

                            return;

                        // private
                        case 'order':
                            if (isFinexEnabled() && event) {
                                switch (event.state) {
                                    case 'wait':
                                    case 'pending':
                                        const orders = selectOpenOrdersList(store.getState());
                                        const updatedOrder = orders.length && orders.find(order => event.uuid && order.uuid === event.uuid);
                                        if (!updatedOrder) {
                                            emitter(alertPush({ message: ['success.order.created'], type: 'success'}));
                                        }
                                        break;
                                    case 'done':
                                        emitter(alertPush({ message: ['success.order.done'], type: 'success'}));
                                        break;
                                    case 'reject':
                                        emitter(alertPush({ message: ['error.order.rejected'], type: 'error'}));
                                        break;
                                    default:
                                        break;
                                }
                            }

                            emitter(rangerUserOrderUpdate(event));

                            return;

                        // private
                        case 'trade':
                            emitter(pushHistoryEmit(event));

                            return;

                        // private
                        case 'balances':
                            emitter(updateWalletsDataByRanger({ ws: true, balances: event }));

                            return;

                        // private
                        case 'deposit_address':
                            emitter(walletsAddressDataWS(event));

                            return;

                        default:
                    }
                    // intentionally ignoring unhandled channels to avoid console noise
                }
            }
        };

        // unsubscribe function
        return () => {
            emitter(rangerDisconnectData());
        };
    });

    return [channel, ws];
};

function* writter(socket: WebSocket, buffer: { messages: object[] }) {
    while (true) {
        const data = yield take(RANGER_DIRECT_WRITE);
        const payload = data.payload;
        
        if (socketReady && currentSocket && currentSocket.readyState === WebSocket.OPEN) {
            console.log('📤 Sending message directly:', payload);
            try {
                currentSocket.send(JSON.stringify(payload));
            } catch (error) {
                console.error('❌ Error sending message:', error);
            }
        } else {
            console.log('⏳ Queueing message for later (socket not ready):', payload);
            writeQueue.push(payload);
        }
    }
}

function* reader(channel) {
    while (true) {
        const action = yield take(channel);
        yield put(action);
    }
}

let previousMarket: Market | undefined;

const switchMarket = (subscribeOnInitOnly: boolean) => {
    return function*(action: SetCurrentMarket) {
        console.debug('🔄 Switch market called:', {
            newMarket: action.payload.id,
            previousMarket: previousMarket && previousMarket.id,
            subscribeOnInitOnly
        });
        
        if (subscribeOnInitOnly && previousMarket !== undefined) {
            console.debug('⏭️ Skipping market switch (subscribeOnInitOnly=true and previous market exists)');
            return;
        }
        if (previousMarket && previousMarket.id !== action.payload.id) {
            console.debug('🔌 Unsubscribing from previous market:', previousMarket.id);
            yield put(rangerUnsubscribeMarket(previousMarket));
        }
        previousMarket = action.payload;
        if (action.payload) {
            console.debug('📡 Subscribing to new market:', action.payload.id);
            yield put(rangerSubscribeMarket(action.payload));
        }
    };
};

function* watchDisconnect(socket: WebSocket, channel: Channel<{}>) {
    yield take(RANGER_DISCONNECT_FETCH);
    socket.close();
}

function* bindSocket(channel: Channel<{}>, socket: WebSocket, buffer: RangerBuffer) {
    return yield all([call(reader, channel), call(writter, socket, buffer), call(watchDisconnect, socket, channel)]);
}

function* dispatchCurrentMarketOrderUpdates(action: UserOrderUpdate) {
    let market;

    try {
        market = yield select(selectCurrentMarket);
    } catch (error) {
        market = undefined;
    }

    if (market && action.payload.market === market.id) {
        yield put(userOpenOrdersUpdate(action.payload));
    }
}

function* dispatchOrderHistoryUpdates(action: UserOrderUpdate) {
    yield put(userOrdersHistoryRangerData(action.payload));
}

function* getSubscriptions() {
    try {
        return yield select(selectSubscriptions);
    } catch (error) {
        return [];
    }
}

export function* rangerSagas() {
    let initialized = false;
    let connectFetchPayload: RangerConnectFetch['payload'] | undefined;
    const buffer: RangerBuffer = { messages: [] };
    let pipes;
    yield takeEvery(MARKETS_SET_CURRENT_MARKET, switchMarket(false));
    yield takeEvery(MARKETS_SET_CURRENT_MARKET_IFUNSET, switchMarket(true));
    yield takeEvery(RANGER_USER_ORDER_UPDATE, dispatchCurrentMarketOrderUpdates);
    yield takeEvery(RANGER_USER_ORDER_UPDATE, dispatchOrderHistoryUpdates);

    while (true) {
        const { connectFetch, disconnectData } = yield race({
            connectFetch: take(RANGER_CONNECT_FETCH),
            disconnectData: take(RANGER_DISCONNECT_DATA),
        });
        let market: Market | undefined;

        if (connectFetch) {
            if (initialized) {
                yield put(rangerDisconnectFetch());
                yield take(RANGER_DISCONNECT_DATA);
            }
            connectFetchPayload = connectFetch.payload;
        }

        if (disconnectData) {
            yield delay(1000);
        }

        try {
            market = yield select(selectCurrentMarket);
        } catch (error) {
            market = undefined;
        }

        if (connectFetchPayload) {
            const prevSubs = yield getSubscriptions();
            const [channel, socket] = yield call(initRanger, connectFetchPayload, market, prevSubs, buffer);
            initialized = true;
            if (pipes) {
                yield cancel(pipes);
            }
            pipes = yield fork(bindSocket, channel, socket, buffer);
        }
    }
}
