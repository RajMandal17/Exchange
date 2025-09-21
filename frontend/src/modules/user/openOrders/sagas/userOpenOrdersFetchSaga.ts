// tslint:disable-next-line
import { call, put } from 'redux-saga/effects';
import { API, RequestOptions } from '../../../../api';
import { alertPush } from '../../../public/alert';
import {
    userOpenOrdersData,
    userOpenOrdersError,
    UserOpenOrdersFetch,
} from '../actions';

const ordersOptions: RequestOptions = {
    apiVersion: 'peatio',
};

export function* userOpenOrdersFetchSaga(action: UserOpenOrdersFetch) {
    try {
        const { market } = action.payload;
        const response = yield call(API.get(ordersOptions), `/market/orders?market=${market.id}&state=wait`);

        // Extract the items array from the PagedList response
        const list = response && response.items ? response.items : [];
        yield put(userOpenOrdersData(list));
    } catch (error) {
        yield put(userOpenOrdersError());
        const errorMessage = error instanceof Error ? error.message : 'Failed to fetch orders';
        const errorCode = error && typeof error === 'object' && 'code' in error ? (error as any).code : undefined;
        yield put(alertPush({message: [errorMessage], code: errorCode, type: 'error'}));
    }
}
