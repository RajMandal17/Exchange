// tslint:disable-next-line
import { call, put, takeLeading } from 'redux-saga/effects';
import { API, RequestOptions } from '../../../../api';
import { getOrderAPI } from '../../../../helpers';
import { alertPush } from '../../alert';
import {
    marketsData,
    marketsError,
    marketsTickersData,
    marketsTickersError,
    MarketsTickersFetch,
    setCurrentMarketIfUnset,
} from '../actions';
import { MARKETS_FETCH, MARKETS_TICKERS_FETCH } from '../constants';

const marketsRequestOptions: RequestOptions = {
    apiVersion: getOrderAPI(),
};

const tickersOptions: RequestOptions = {
    apiVersion: 'peatio',
};

export function* rootMarketsSaga() {
    yield takeLeading(MARKETS_FETCH, marketsFetchSaga);
    yield takeLeading(MARKETS_TICKERS_FETCH, tickersSaga);
}

export function* marketsFetchSaga() {
    try {
        const markets = yield call(API.get(marketsRequestOptions), '/public/markets');
        yield put(marketsData(markets));
        yield put(setCurrentMarketIfUnset(markets[0]));
    } catch (error) {
        yield put(marketsError());
        yield put(alertPush({message: error.message, code: error.code, type: 'error'}));
    }
}

export function* tickersSaga(action: MarketsTickersFetch) {
    try {
        console.debug('📡 Fetching initial ticker data from API...');
        const tickers = yield call(API.get(tickersOptions), `/public/markets/tickers`);
        console.debug('✅ Initial ticker data received:', tickers);

        if (tickers) {
            // The API response is already in the correct format: { "BTC-USDT": {...}, "ETH-USDT": {...} }
            // No need to convert like the old code did with tickers[pair].ticker
            yield put(marketsTickersData(tickers));
            console.debug('✅ Ticker data stored in Redux');
        }
    } catch (error) {
        console.error('❌ Failed to fetch initial ticker data:', error);
        yield put(marketsTickersError());
        const errorMessage = error instanceof Error ? error.message : 'Unknown error';
        const errorCode = (error as any).code || 500;
        yield put(alertPush({message: [errorMessage], code: errorCode, type: 'error'}));
    }
}
