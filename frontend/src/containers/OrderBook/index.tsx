import classNames from 'classnames';
import * as React from 'react';
import { Spinner } from 'react-bootstrap';
import { injectIntl } from 'react-intl';
import { connect, MapDispatchToPropsFunction } from 'react-redux';
import { CombinedOrderBook, Decimal } from '../../components';
import { colors } from '../../constants';
import { accumulateVolume, calcMaxVolume } from '../../helpers';
import { IntlProps } from '../../index';
import {
    Market,
    RootState,
    selectCurrentColorTheme,
    selectCurrentMarket,
    selectCurrentPrice,
    selectDepthAsks,
    selectDepthBids,
    selectDepthLoading,
    selectMarketTickers,
    selectMobileDeviceState,
    selectOpenOrdersList,
    setCurrentPrice,
    Ticker,
} from '../../modules';
import { OrderCommon } from '../../modules/types';

interface ReduxProps {
    asks: string[][];
    bids: string[][];
    colorTheme: string;
    currentMarket?: Market;
    currentPrice?: number;
    openOrdersList: OrderCommon[];
    orderBookLoading: boolean;
    isMobileDevice: boolean;
}

interface DispatchProps {
    setCurrentPrice: typeof setCurrentPrice;
}

interface State {
    width: number;
}

interface OwnProps {
    marketTickers: {
        [key: string]: Ticker;
    };
    forceLarge?: boolean;
}

type Props = ReduxProps & DispatchProps & OwnProps & IntlProps;

// render big/small breakpoint
const breakpoint = 448;
// ...existing code...
class OrderBookContainer extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);

        this.state = {
            width: 0,
        };

        this.orderRef = React.createRef();
    }

    private orderRef: React.RefObject<HTMLDivElement>;

    // cache last non-zero ticker per market id
    private cachedTickers: { [marketId: string]: Ticker } = {};

    // Force component update when mobile/desktop mode changes
    private lastIsLargeMode: boolean = false;

    // Consistent layout mode detection
    private getIsLargeMode = (): boolean => {
        const { forceLarge } = this.props;
        
        return forceLarge || this.state.width > breakpoint;
    };

    public componentDidMount() {
        this.lastIsLargeMode = this.getIsLargeMode();
    }

    public componentDidUpdate() {
        if (this.orderRef.current && this.state.width !== this.orderRef.current.clientWidth) {
            this.setState({
                width: this.orderRef.current.clientWidth,
            });
        }

        // Track layout mode changes
        const currentIsLargeMode = this.getIsLargeMode();
        if (currentIsLargeMode !== this.lastIsLargeMode) {
            this.lastIsLargeMode = currentIsLargeMode;
            // Force re-render when layout mode changes
            this.forceUpdate();
        }
    }

    public shouldComponentUpdate(nextProps: Props, nextState: State) {
        const { asks, bids, currentMarket, openOrdersList, marketTickers, orderBookLoading, isMobileDevice, forceLarge } = this.props;

        const lastPrice = currentMarket && this.getTickerValue(currentMarket, marketTickers).last;
        const nextLastPrice = nextProps.currentMarket && this.getTickerValue(nextProps.currentMarket, nextProps.marketTickers).last;

        // Check if layout mode changes (mobile/desktop)
        const currentIsLarge = this.getIsLargeMode();
        const nextIsLarge = nextProps.forceLarge || (nextState.width > breakpoint);

        // Log ticker updates for debugging
        if (currentMarket && nextProps.currentMarket && 
            currentMarket.id === nextProps.currentMarket.id && 
            JSON.stringify(marketTickers[currentMarket.id]) !== JSON.stringify(nextProps.marketTickers[currentMarket.id])) {
            console.debug('🔄 OrderBook ticker data changed for', currentMarket.id, {
                old: marketTickers[currentMarket.id],
                new: nextProps.marketTickers[currentMarket.id],
                isLarge: currentIsLarge,
                isMobile: isMobileDevice
            });
        }

        return (
            JSON.stringify(nextProps.asks) !== JSON.stringify(asks) ||
            JSON.stringify(nextProps.bids) !== JSON.stringify(bids) ||
            (nextProps.currentMarket && nextProps.currentMarket.id) !== (currentMarket && currentMarket.id) ||
            nextLastPrice !== lastPrice ||
            nextProps.openOrdersList !== openOrdersList ||
            nextProps.orderBookLoading !== orderBookLoading ||
            nextProps.isMobileDevice !== isMobileDevice ||
            nextProps.forceLarge !== forceLarge ||
            nextState.width !== this.state.width ||
            nextIsLarge !== currentIsLarge ||
            // Force update when ticker data changes
            JSON.stringify(nextProps.marketTickers) !== JSON.stringify(marketTickers)
        );
    }

    public render() {
        const {
            asks,
            bids,
            orderBookLoading,
        } = this.props;

        const isLarge = this.getIsLargeMode();

        const cn = classNames('pg-combined-order-book ', {
            'cr-combined-order-book--data-loading': orderBookLoading,
            'pg-combined-order-book--no-data-first': (!asks.length && !isLarge) || (!bids.length && isLarge),
            'pg-combined-order-book--no-data-second': (!bids.length && !isLarge) || (!asks.length && isLarge),
        });

        return (
            <div className={cn} ref={this.orderRef}>
                <div className={'cr-table-header__content'}>
                    {this.props.intl.formatMessage({id: 'page.body.trade.orderbook'})}
                </div>
                {orderBookLoading ? <div className="pg-combined-order-book-loader"><Spinner animation="border" variant="primary" /></div> : this.orderBook(bids, asks)}
            </div>
        );
    }

    private orderBook = (bids, asks) => {
        const {
            colorTheme,
            currentMarket,
        } = this.props;

        const isLarge = this.getIsLargeMode();
        const asksData = isLarge ? asks : asks.slice(0).reverse();

        return (
            <CombinedOrderBook
                maxVolume={calcMaxVolume(bids, asks)}
                orderBookEntryAsks={accumulateVolume(asks)}
                orderBookEntryBids={accumulateVolume(bids)}
                rowBackgroundColorAsks={colors[colorTheme].orderBook.asks}
                rowBackgroundColorBids={colors[colorTheme].orderBook.bids}
                dataAsks={this.renderOrderBook(asksData, 'asks', this.props.intl.formatMessage({id: 'page.noDataToShow'}), currentMarket)}
                dataBids={this.renderOrderBook(bids, 'bids', this.props.intl.formatMessage({id: 'page.noDataToShow'}), currentMarket)}
                headers={this.renderHeaders()}
                lastPrice={this.lastPrice()}
                onSelectAsks={this.handleOnSelectAsks}
                onSelectBids={this.handleOnSelectBids}
                isLarge={isLarge}
            />
        );
    };

    private lastPrice = () => {
        const { currentMarket, isMobileDevice, marketTickers } = this.props;
        const currentTicker = currentMarket && this.getTickerValue(currentMarket, marketTickers);

        if (currentMarket && currentTicker) {
            const cn = classNames('', {
                'cr-combined-order-book__market-negative': String(currentTicker.price_change_percent).includes('-'),
                'cr-combined-order-book__market-positive': String(currentTicker.price_change_percent).includes('+'),
            });

            return (
                <React.Fragment>
                    <span className={cn}>
                        {Decimal.format(+(currentTicker.last), currentMarket.price_precision)}&nbsp;
                        {isMobileDevice ? null : currentMarket.quote_unit.toUpperCase()}
                    </span>
                    <span>{this.props.intl.formatMessage({id: 'page.body.trade.orderbook.lastMarket'})}</span>
                </React.Fragment>
            );
        } else {
          return <React.Fragment><span className={'cr-combined-order-book__market-negative'}>0</span><span>{this.props.intl.formatMessage({id: 'page.body.trade.orderbook.lastMarket'})}</span></React.Fragment>;
        }
    };

    private renderHeaders = () => {
        const {
            currentMarket,
            intl,
            isMobileDevice,
        } = this.props;
        const formattedBaseUnit = (currentMarket && currentMarket.base_unit) ? `(${currentMarket.base_unit.toUpperCase()})` : '';
        const formattedQuoteUnit = (currentMarket && currentMarket.quote_unit) ? `(${currentMarket.quote_unit.toUpperCase()})` : '';

        if (isMobileDevice) {
            return [
                `${intl.formatMessage({id: 'page.body.trade.orderbook.header.price'})}\n${formattedQuoteUnit}`,
                `${intl.formatMessage({id: 'page.body.trade.orderbook.header.amount'})}\n${formattedBaseUnit}`,
            ];
        }

        return [
            `${intl.formatMessage({id: 'page.body.trade.orderbook.header.price'})}\n${formattedQuoteUnit}`,
            `${intl.formatMessage({id: 'page.body.trade.orderbook.header.amount'})}\n${formattedBaseUnit}`,
            `${intl.formatMessage({id: 'page.body.trade.orderbook.header.volume'})}\n${formattedBaseUnit}`,
        ];
    };

    private renderOrderBook = (array: string[][], side: string, message: string, currentMarket?: Market) => {
        const { isMobileDevice } = this.props;
        let total = accumulateVolume(array);
        const isLarge = this.getIsLargeMode();
        const priceFixed = currentMarket ? currentMarket.price_precision : 0;
        const amountFixed = currentMarket ? currentMarket.amount_precision : 0;

        return (array.length > 0) ? array.map((item, i) => {
            const [price, volume] = item;
            switch (side) {
                case 'asks':
                    total = isLarge ? accumulateVolume(array) : accumulateVolume(array.slice(0).reverse()).slice(0).reverse();

                    if (isMobileDevice) {
                        return [
                            <span key={i}><Decimal fixed={priceFixed} prevValue={array[i + 1] ? array[i + 1][0] : 0}>{price}</Decimal></span>,
                            <Decimal key={i} fixed={amountFixed}>{total[i]}</Decimal>,
                        ];
                    }

                    return [
                        <span key={i}><Decimal fixed={priceFixed} prevValue={array[i + 1] ? array[i + 1][0] : 0}>{price}</Decimal></span>,
                        <Decimal key={i} fixed={amountFixed}>{volume}</Decimal>,
                        <Decimal key={i} fixed={amountFixed}>{total[i]}</Decimal>,
                    ];
                default:
                    if (isLarge) {
                        if (isMobileDevice) {
                            return [
                                <Decimal key={i} fixed={amountFixed}>{total[i]}</Decimal>,
                                <span key={i}><Decimal fixed={priceFixed} prevValue={array[i - 1] ? array[i - 1][0] : 0}>{price}</Decimal></span>,
                            ];
                        }

                        return [
                            <Decimal key={i} fixed={amountFixed}>{total[i]}</Decimal>,
                            <Decimal key={i} fixed={amountFixed}>{volume}</Decimal>,
                            <span key={i}><Decimal fixed={priceFixed} prevValue={array[i - 1] ? array[i - 1][0] : 0}>{price}</Decimal></span>,
                        ];
                    } else {
                        if (isMobileDevice) {
                            return [
                                <span key={i}><Decimal fixed={priceFixed} prevValue={array[i - 1] ? array[i - 1][0] : 0}>{price}</Decimal></span>,
                                <Decimal key={i} fixed={amountFixed}>{total[i]}</Decimal>,
                            ];
                        }

                        return [
                            <span key={i}><Decimal fixed={priceFixed} prevValue={array[i - 1] ? array[i - 1][0] : 0}>{price}</Decimal></span>,
                            <Decimal key={i} fixed={amountFixed}>{volume}</Decimal>,
                            <Decimal key={i} fixed={amountFixed}>{total[i]}</Decimal>,
                        ];
                    }
            }
        }) : [[[''], message, ['']]];
    };

    private handleOnSelectBids = (index: string) => {
        const { currentPrice, bids } = this.props;
        const priceToSet = bids[Number(index)] && Number(bids[Number(index)][0]);
        if (currentPrice !== priceToSet) {
            this.props.setCurrentPrice(priceToSet);
        }
    };

    private handleOnSelectAsks = (index: string) => {
        const { asks, currentPrice } = this.props;
        const isLarge = this.getIsLargeMode();
        const asksData = isLarge ? asks : asks.slice(0).reverse();
        const priceToSet = asksData[Number(index)] && Number(asksData[Number(index)][0]);
        if (currentPrice !== priceToSet) {
            this.props.setCurrentPrice(priceToSet);
        }
    };

    private getTickerValue = (currentMarket: Market, tickers: { [key: string]: Ticker }) => {
        const defaultTicker: any = { amount: 0, low: 0, last: 0, high: 0, volume: 0, open: 0, price_change_percent: '+0.00%' };

        if (!currentMarket) {
            return defaultTicker as Ticker;
        }

        const marketId = currentMarket.id;
        const incoming = tickers && tickers[marketId];

        // If incoming ticker exists and has a valid non-zero last price, cache and return it
        if (incoming) {
            const lastVal = Number(incoming.last);
            const isValidPrice = !isNaN(lastVal) && lastVal > 0;
            
            if (isValidPrice) {
                // Cache the entire ticker when we have valid data
                this.cachedTickers[marketId] = { ...incoming };
                return incoming;
            }

            // incoming last is zero/invalid -> return cached if present, otherwise return incoming (to preserve other fields)
            const cached = this.cachedTickers[marketId];
            if (cached) {
                // Merge incoming data with cached data, prioritizing cached prices
                return {
                    ...incoming,
                    last: cached.last,
                    low: incoming.low && Number(incoming.low) > 0 ? incoming.low : cached.low,
                    high: incoming.high && Number(incoming.high) > 0 ? incoming.high : cached.high,
                    volume: incoming.volume && Number(incoming.volume) > 0 ? incoming.volume : cached.volume,
                };
            }
            
            return incoming || (defaultTicker as Ticker);
        }

        // No incoming ticker -> return cached if present, otherwise default
        return this.cachedTickers[marketId] || (defaultTicker as Ticker);
    };
}
// ...existing code...

const mapStateToProps = (state: RootState) => ({
    bids: selectDepthBids(state),
    asks: selectDepthAsks(state),
    colorTheme: selectCurrentColorTheme(state),
    orderBookLoading: selectDepthLoading(state),
    currentMarket: selectCurrentMarket(state),
    currentPrice: selectCurrentPrice(state),
    marketTickers: selectMarketTickers(state),
    openOrdersList: selectOpenOrdersList(state),
    isMobileDevice: selectMobileDeviceState(state),
});

const mapDispatchToProps: MapDispatchToPropsFunction<DispatchProps, {}> =
    dispatch => ({
        setCurrentPrice: payload => dispatch(setCurrentPrice(payload)),
    });

export const OrderBook = injectIntl(connect(mapStateToProps, mapDispatchToProps)(OrderBookContainer)) as any;
export type OrderBookProps = ReduxProps;
