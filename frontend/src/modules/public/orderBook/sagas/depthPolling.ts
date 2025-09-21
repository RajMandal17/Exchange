import { call, put, take, cancel } from 'redux-saga/effects';
import { API, RequestOptions } from '../../../../api';
import { CommonError } from '../../../types';
import {
    depthData,
    depthError,
    DepthFetch,
} from '../actions';

const depthOptions: RequestOptions = {
    apiVersion: 'peatio',
};

export function* depthSaga(action: DepthFetch) {
    try {
        const market = action.payload;
        console.log('📊 Depth saga fetching data for market:', market.id);
        const depth = yield call(API.get(depthOptions), `/public/markets/${market.id}/depth`);
        console.log('✅ Depth data received:', depth);
        yield put(depthData(depth));
    } catch (error) {
        console.error('❌ Depth fetch error:', error);
        const commonError: CommonError = {
            code: 500,
            message: [(error as Error).message || 'Unknown error occurred'],
        };
        yield put(depthError(commonError));
    }
}

// This function is for real-time updates via ranger WebSocket - no additional WebSocket connection needed
export function* watchOrderBookWebSocket() {
    console.log('Order book WebSocket watcher started - using existing ranger connection');
    // The ranger saga already handles WebSocket connections and market subscriptions
    // Order book updates will come through the ranger saga's WebSocket connection
    // and be dispatched as depthData, depthDataSnapshot, or depthDataIncrement actions
    yield; // This just keeps the function as a generator function
}

export function* watchOrderBookPolling() {
    let pollingTask = null;
    
    while (true) {
        yield take(['@@router/LOCATION_CHANGE', 'MARKET_SELECTOR_DATA']);
        
        // Cancel existing polling
        if (pollingTask) {
            yield cancel(pollingTask);
            pollingTask = null;
        }
        
        // Polling disabled - using WebSocket real-time updates instead
        console.log('Order book polling disabled - using WebSocket real-time updates');
    }
}
