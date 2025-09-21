import { takeLatest, fork } from 'redux-saga/effects';
import {
    DEPTH_FETCH,
    ORDER_BOOK_FETCH,
} from '../constants';
import { depthSaga, watchOrderBookWebSocket } from './depthPolling';
import { orderBookSaga } from './orderBookSaga';

export function* rootOrderBookSaga() {
    yield takeLatest(ORDER_BOOK_FETCH, orderBookSaga);
    yield takeLatest(DEPTH_FETCH, depthSaga);
    // Watch for market changes and handle WebSocket order book updates
    yield fork(watchOrderBookWebSocket);
}
