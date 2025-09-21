import { call, put } from 'redux-saga/effects';
import { alertPush } from '../../..';
import { API, RequestOptions } from '../../../../api';
import {
    userOrdersHistoryData,
    userOrdersHistoryError,
    UserOrdersHistoryFetch,
} from '../actions';

const ordersOptions: RequestOptions = {
    apiVersion: 'peatio',
};

export function* ordersHistorySaga(action: UserOrdersHistoryFetch) {
    try {
        const { pageIndex, limit, type } = action.payload;
        const params = `${type === 'all' ? '' : '&state=wait'}`;
        const response = yield call(API.get(ordersOptions), `/market/orders?page=${pageIndex + 1}&limit=${limit}${params}`);
        
        // Extract the items array from the PagedList response
        const data = response && response.items ? response.items : [];
        let nextPageExists = false;

        if (data.length === limit) {
            const checkResponse = yield call(API.get(ordersOptions), `/market/orders?page=${(pageIndex + 1) * limit + 1}&limit=${1}${params}`);
            const checkData = checkResponse && checkResponse.items ? checkResponse.items : [];

            if (checkData.length === 1) {
                nextPageExists = true;
            }
        }

        yield put(userOrdersHistoryData({ list: data, nextPageExists, pageIndex }));
    } catch (error) {
        yield put(userOrdersHistoryError());
        const errorMessage = error instanceof Error ? error.message : 'Failed to fetch order history';
        const errorCode = error && typeof error === 'object' && 'code' in error ? (error as any).code : undefined;
        yield put(alertPush({message: [errorMessage], code: errorCode, type: 'error'}));
    }
}
