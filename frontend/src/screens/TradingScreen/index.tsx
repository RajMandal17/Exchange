import * as React from 'react';
import { injectIntl } from 'react-intl';
import { connect, MapDispatchToPropsFunction, MapStateToProps } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { compose } from 'redux';
import { incrementalOrderBook } from '../../api';
import { Decimal } from '../../components/Decimal';
import { GridChildInterface, GridItem } from '../../components/GridItem';
import {
    MarketDepthsComponent,
    MarketsComponent,
    OpenOrdersComponent,
    OrderBook,
    OrderComponent,
    RecentTrades,
    ToolBar,
    TradingChart,
} from '../../containers';
import { getUrlPart, setDocumentTitle } from '../../helpers';
import { IntlProps } from '../../index';
import {
    RootState,
    selectCurrentMarket,
    selectMarketTickers,
    selectUserInfo,
    selectUserLoggedIn,
    setCurrentMarket,
    setCurrentPrice,
    Ticker,
    User,
} from '../../modules';
import { GridLayoutState, saveLayouts, selectGridLayoutState } from '../../modules/public/gridLayout';
import { Market, marketsFetch, selectMarkets } from '../../modules/public/markets';
import { depthFetch } from '../../modules/public/orderBook';
import { rangerConnectFetch, RangerConnectFetch } from '../../modules/public/ranger';
import { RangerState } from '../../modules/public/ranger/reducer';
import { selectRanger } from '../../modules/public/ranger/selectors';

const { WidthProvider, Responsive } = require('react-grid-layout');

const breakpoints = {
    lg: 1200,
    md: 996,
    sm: 768,
    xs: 480,
    xxs: 0,
};

const cols = {
    lg: 24,
    md: 24,
    sm: 12,
    xs: 12,
    xxs: 12,
};

interface ReduxProps {
    currentMarket: Market | undefined;
    markets: Market[];
    user: User;
    rangerState: RangerState;
    userLoggedIn: boolean;
    rgl: GridLayoutState;
    tickers: {
        [pair: string]: Ticker,
    };
}

interface DispatchProps {
    depthFetch: typeof depthFetch;
    marketsFetch: typeof marketsFetch;
    rangerConnect: typeof rangerConnectFetch;
    setCurrentPrice: typeof setCurrentPrice;
    setCurrentMarket: typeof setCurrentMarket;
    saveLayouts: typeof saveLayouts;
}

interface StateProps {
    orderComponentResized: number;
    orderBookComponentResized: number;
}

const ReactGridLayout = WidthProvider(Responsive);
type Props = DispatchProps & ReduxProps & RouteComponentProps & IntlProps;

const TradingWrapper = props => {
    const { orderComponentResized, orderBookComponentResized, layouts, handleResize } = props;
    const children = React.useMemo(() => {
        const data = [
            {
                i: 1,
                render: () => <OrderComponent size={orderComponentResized} />,
            },
            {
                i: 2,
                render: () => <TradingChart />,
            },
            {
                i: 3,
                render: () => <OrderBook size={orderBookComponentResized} />,
            },
            {
                i: 4,
                render: () => <MarketDepthsComponent />,
            },
            {
                i: 5,
                render: () => <OpenOrdersComponent/>,
            },
            {
                i: 6,
                render: () => <RecentTrades/>,
            },
            {
                i: 7,
                render: () => <MarketsComponent/>,
            },
        ];

        return data.map((child: GridChildInterface) => (
            <div key={child.i}>
                <GridItem>{child.render ? child.render() : `Child Body ${child.i}`}</GridItem>
            </div>
        ));
    }, [orderComponentResized, orderBookComponentResized]);

    return (
        <ReactGridLayout
            breakpoints={breakpoints}
            cols={cols}
            draggableHandle=".cr-table-header__content, .pg-trading-screen__tab-panel, .draggable-container"
            rowHeight={14}
            layouts={layouts}
            onLayoutChange={() => {return;}}
            margin={[5, 5]}
            onResize={handleResize}
        >
            {children}
        </ReactGridLayout>
    );
};

class Trading extends React.Component<Props, StateProps> {
    public readonly state = {
        orderComponentResized: 5,
        orderBookComponentResized: 5,
    };

    public componentDidMount() {
        setDocumentTitle('Trading');
        const { markets, currentMarket, userLoggedIn, rangerState: { connected, withAuth } } = this.props;

        console.log('🏪 TradingScreen componentDidMount');
        console.log('📊 Markets loaded:', markets.length);
        console.log('🎯 Current market:', currentMarket);
        console.log('🔗 Ranger connected:', connected, 'withAuth:', withAuth);
        console.log('👤 User logged in:', userLoggedIn);

        if (markets.length < 1) {
            console.log('📡 Fetching markets...');
            this.props.marketsFetch();
        } else {
            // If markets are already loaded, ensure we have a current market selected
            this.setMarketFromUrlIfExists(markets);
        }

        if (currentMarket && !incrementalOrderBook()) {
            console.log('📈 Fetching depth for market:', currentMarket.id);
            this.props.depthFetch(currentMarket);
        } else if (!currentMarket) {
            console.log('⚠️ No current market selected');
        }

        if (!connected) {
            console.log('🔌 Connecting to ranger...');
            this.props.rangerConnect({ withAuth: userLoggedIn });
        }

        if (userLoggedIn && !withAuth) {
            console.log('🔐 Reconnecting ranger with auth...');
            this.props.rangerConnect({ withAuth: userLoggedIn });
        }
    }

    public componentWillUnmount() {
        this.props.setCurrentPrice(undefined);
    }

    public componentWillReceiveProps(nextProps) {
        const {
            history,
            markets,
            userLoggedIn,
        } = this.props;

        if (userLoggedIn !== nextProps.userLoggedIn) {
            this.props.rangerConnect({ withAuth: nextProps.userLoggedIn });
        }

        if (markets.length !== nextProps.markets.length) {
            this.setMarketFromUrlIfExists(nextProps.markets);
        }

        // Check if current market has changed and fetch depth accordingly
        const currentMarketId = this.props.currentMarket && this.props.currentMarket.id;
        const nextMarketId = nextProps.currentMarket && nextProps.currentMarket.id;
        
        if (currentMarketId !== nextMarketId && nextProps.currentMarket) {
            console.log('🔄 Market changed from', currentMarketId, 'to', nextMarketId);
            
            // Update URL to match the new market
            const marketFromUrl = history.location.pathname.split('/');
            const marketNotMatched = nextProps.currentMarket.id !== marketFromUrl[marketFromUrl.length - 1];
            if (marketNotMatched) {
                console.log('🔗 Updating URL to match market:', nextProps.currentMarket.id);
                history.replace(`/trading/${nextProps.currentMarket.id}`);
            }
            
            // Fetch depth for the new market (if not using incremental order book)
            if (!incrementalOrderBook()) {
                console.log('📈 Fetching depth for new market:', nextProps.currentMarket.id);
                this.props.depthFetch(nextProps.currentMarket);
            }
            
            // Reconnect ranger with new market streams
            console.log('🔁 Reconnecting ranger for new market:', nextProps.currentMarket.id);
            this.props.rangerConnect({ withAuth: nextProps.userLoggedIn });
        }

        if (nextProps.currentMarket && nextProps.tickers) {
            this.setTradingTitle(nextProps.currentMarket, nextProps.tickers);
        }
    }

    public render() {
        const { orderComponentResized, orderBookComponentResized } = this.state;
        const { rgl } = this.props;

        return (
            <div className={'pg-trading-screen'}>
                <div className={'pg-trading-wrap'}>
                    <ToolBar/>
                    <div data-react-toolbox="grid" className={'cr-grid'}>
                        <div className="cr-grid__grid-wrapper">
                            <TradingWrapper
                                layouts={rgl.layouts}
                                orderComponentResized={orderComponentResized}
                                orderBookComponentResized={orderBookComponentResized}
                                handleResize={this.handleResize}
                            />
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    // private setMarketFromUrlIfExists = (markets: Market[]): void => {
    //     const urlMarket: string = getUrlPart(2, window.location.pathname);
    //     const market: Market | undefined = markets.find(item => item.id === urlMarket);

    //     if (market) {
    //         console.log('🎯 Setting market from URL:', market.id);
    //         this.props.setCurrentMarket(market);
    //     } else if (markets.length > 0 && !this.props.currentMarket) {
    //         // Set first market as default if no market is selected and markets are available
    //         const defaultMarket = markets[0];
    //         console.log('🎯 Setting default market:', defaultMarket.id);
    //         this.props.setCurrentMarket(defaultMarket);
    //     }
    // };

    private setMarketFromUrlIfExists = (markets: Market[]): void => {
        const urlMarket: string = getUrlPart(2, window.location.pathname);
        const market: Market | undefined = markets.find(item => item.id === urlMarket);

        if (market) {
            console.log('🎯 Setting market from URL:', market.id);
            this.props.setCurrentMarket(market);
            
            // Reconnect ranger with market-specific streams
            console.log('🔌 Reconnecting ranger for URL market:', market.id);
            this.props.rangerConnect({ withAuth: this.props.userLoggedIn });
        } else if (markets.length > 0 && !this.props.currentMarket) {
            // Set first market as default if no market is selected and markets are available
            const defaultMarket = markets[0];
            console.log('🎯 Setting default market:', defaultMarket.id);
            this.props.setCurrentMarket(defaultMarket);
            
            // Reconnect ranger with default market streams
            console.log('🔌 Reconnecting ranger for default market:', defaultMarket.id);
            this.props.rangerConnect({ withAuth: this.props.userLoggedIn });
        }
    };

    private setTradingTitle = (market: Market, tickers: ReduxProps['tickers']) => {
        const tickerPrice = tickers[market.id] ? tickers[market.id].last : '0.0';
        document.title = `${Decimal.format(tickerPrice, market.price_precision)} ${market.name}`;
    };

    

    private handleResize = (layout, oldItem, newItem) => {
        switch (oldItem.i) {
            case '1':
                this.setState({
                    orderComponentResized: newItem.w,
                });
                break;
            case '3':
                this.setState({
                    orderBookComponentResized: newItem.w,
                });
                break;
            default:
                break;
        }
    };
}

const mapStateToProps: MapStateToProps<ReduxProps, {}, RootState> = state => ({
    currentMarket: selectCurrentMarket(state),
    markets: selectMarkets(state),
    user: selectUserInfo(state),
    rangerState: selectRanger(state),
    userLoggedIn: selectUserLoggedIn(state),
    rgl: selectGridLayoutState(state),
    tickers: selectMarketTickers(state),
});

const mapDispatchToProps: MapDispatchToPropsFunction<DispatchProps, {}> = dispatch => ({
    marketsFetch: () => dispatch(marketsFetch()),
    depthFetch: payload => dispatch(depthFetch(payload)),
    rangerConnect: (payload: RangerConnectFetch['payload']) => dispatch(rangerConnectFetch(payload)),
    setCurrentPrice: payload => dispatch(setCurrentPrice(payload)),
    setCurrentMarket: payload => dispatch(setCurrentMarket(payload)),
    saveLayouts: payload => dispatch(saveLayouts(payload)),
});

export const TradingScreen = compose(
    injectIntl,
    withRouter,
    connect(mapStateToProps, mapDispatchToProps),
)(Trading) as React.ComponentClass;
