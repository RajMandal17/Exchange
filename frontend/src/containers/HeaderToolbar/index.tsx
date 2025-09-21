import * as React from 'react';
import { injectIntl } from 'react-intl';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';
import { Decimal } from '../../components/Decimal';
import { IntlProps } from '../../index';
import {
    Market,
    marketsTickersFetch,
    RootState,
    selectCurrentMarket,
    selectMarkets,
    selectMarketTickers, Ticker,
} from '../../modules';


interface ReduxProps {
    currentMarket?: Market;
    markets: Market[];
    marketTickers: {
        [key: string]: Ticker,
    };
}

interface DispatchProps {
    marketsTickersFetch: typeof marketsTickersFetch;
}

type Props = IntlProps & ReduxProps & DispatchProps;

// tslint:disable no-any jsx-no-multiline-js
class HeaderToolbarContainer extends React.Component<Props> {
    // cache last non-zero ticker per market id
    private cachedTickers: { [marketId: string]: Ticker } = {};

    public componentDidMount() {
        // Fetch initial ticker data when component mounts
        console.debug('🚀 HeaderToolbar mounted, fetching initial ticker data...');
        this.props.marketsTickersFetch();
    }

    public shouldComponentUpdate(nextProps: Props) {
        const { currentMarket, markets, marketTickers } = this.props;

        // Log ticker updates for debugging
        if (currentMarket && nextProps.currentMarket && 
            currentMarket.id === nextProps.currentMarket.id && 
            JSON.stringify(marketTickers[currentMarket.id]) !== JSON.stringify(nextProps.marketTickers[currentMarket.id])) {
            console.debug('🔄 HeaderToolbar ticker data changed for', currentMarket.id, {
                old: marketTickers[currentMarket.id],
                new: nextProps.marketTickers[currentMarket.id]
            });
        }

        return (
            (nextProps.currentMarket && nextProps.currentMarket.id) !== (currentMarket && currentMarket.id) ||
            nextProps.markets !== markets ||
            JSON.stringify(nextProps.marketTickers) !== JSON.stringify(marketTickers)
        );
    }

    public render() {
        const { marketTickers, currentMarket } = this.props;
        const defaultTicker = { amount: 0, low: 0, last: 0, high: 0, volume: 0, price_change_percent: '+0.00%' };

        const isPositive = currentMarket && /\+/.test(this.getTickerValue('price_change_percent'));
        const cls = isPositive ? 'positive' : 'negative';

        const bidUnit = currentMarket && currentMarket.quote_unit.toUpperCase();

        return (
            <div className="pg-header__toolbar">
                <div className="pg-header__toolbar-item">
                    <p className="pg-header__toolbar-item-value pg-header__toolbar-item-value-positive">
                        {currentMarket && Decimal.format(Number(this.getTickerValue('low')), currentMarket.price_precision)} {bidUnit}
                    </p>
                    <p className="pg-header__toolbar-item-text">
                        {this.translate('page.body.trade.toolBar.lowest')}
                    </p>
                </div>
                <div className="pg-header__toolbar-item">
                    <p className="pg-header__toolbar-item-value pg-header__toolbar-item-value-negative">
                        {currentMarket && Decimal.format(Number(this.getTickerValue('last')), currentMarket.price_precision)} {bidUnit}
                    </p>
                    <p className="pg-header__toolbar-item-text">
                        {this.translate('page.body.trade.toolBar.lastPrice')}
                    </p>
                </div>
                <div className="pg-header__toolbar-item">
                    <p className="pg-header__toolbar-item-value pg-header__toolbar-item-value-negative">
                        {currentMarket && Decimal.format(Number(this.getTickerValue('high')), currentMarket.price_precision)} {bidUnit}
                    </p>
                    <p className="pg-header__toolbar-item-text">
                        {this.translate('page.body.trade.toolBar.highest')}
                    </p>
                </div>
                <div className="pg-header__toolbar-item">
                    <p className="pg-header__toolbar-item-value pg-header__toolbar-item-value-positive">
                        {currentMarket && Decimal.format(Number(this.getTickerValue('volume')), currentMarket.price_precision)} {bidUnit}
                    </p>
                    <p className="pg-header__toolbar-item-text">
                        {this.translate('page.body.trade.toolBar.volume')}
                    </p>
                </div>
                <div className="pg-header__toolbar-item">
                    <p className={`pg-header__toolbar-item-value pg-header__toolbar-item-value-${cls}`}>
                        {currentMarket && (marketTickers[currentMarket.id] || defaultTicker).price_change_percent}
                    </p>
                    <p className="pg-header__toolbar-item-text">
                        {this.translate('page.body.trade.toolBar.change')}
                    </p>
                </div>
            </div>
        );
    }

    private getTickerValue = (value: string) => {
        const { marketTickers, currentMarket } = this.props;
        const defaultTicker = { amount: 0, low: 0, last: 0, high: 0, volume: 0, price_change_percent: '+0.00%'};

        if (!currentMarket) {
            console.debug('⚠️ HeaderToolbar: No currentMarket available');
            return (defaultTicker as any)[value];
        }

        const marketId = currentMarket.id;
        const incoming: Ticker | undefined = marketTickers && marketTickers[marketId];

        console.debug('📊 HeaderToolbar getTickerValue:', {
            marketId,
            value,
            hasIncoming: !!incoming,
            incomingData: incoming,
            allMarketTickers: Object.keys(marketTickers || {}),
            hasCached: !!this.cachedTickers[marketId]
        });

        if (incoming) {
            const lastVal = (incoming as any).last;
            const lastNum = Number(lastVal);

            // Always cache incoming ticker if it has a valid last price > 0
            if (lastVal !== undefined && !isNaN(lastNum) && lastNum > 0) {
                this.cachedTickers[marketId] = incoming;
                console.debug('💾 HeaderToolbar cached ticker for', marketId, 'last:', lastVal);
                return (incoming as any)[value];
            }

            // If incoming last is 0 or invalid, try cached first
            const cached = this.cachedTickers[marketId];
            if (cached && (cached as any)[value] !== undefined && (cached as any).last > 0) {
                console.debug('📤 HeaderToolbar using cached value for', marketId, value, ':', (cached as any)[value]);
                return (cached as any)[value];
            }

            // Fallback to incoming (even if last is 0) to show other data
            console.debug('⚠️ HeaderToolbar using incoming ticker with 0 value for', marketId, value, ':', (incoming as any)[value]);
            return (incoming as any)[value] !== undefined ? (incoming as any)[value] : (defaultTicker as any)[value];
        }

        // No incoming ticker -> use cached if available
        const cached = this.cachedTickers[marketId];
        if (cached && (cached as any)[value] !== undefined) {
            console.debug('📤 HeaderToolbar using cached (no incoming) for', marketId, value, ':', (cached as any)[value]);
            return (cached as any)[value];
        }

        console.debug('❌ HeaderToolbar returning default for', marketId, value, ':', (defaultTicker as any)[value]);
        return (defaultTicker as any)[value];
    };

    private translate = (id: string) => {
        return id ? this.props.intl.formatMessage({ id }) : '';
    };
}

const mapStateToProps = (state: RootState): ReduxProps => ({
    currentMarket: selectCurrentMarket(state),
    markets: selectMarkets(state),
    marketTickers: selectMarketTickers(state),
});

const mapDispatchToProps: DispatchProps = {
    marketsTickersFetch,
};

const HeaderToolbar = injectIntl(withRouter(connect(mapStateToProps, mapDispatchToProps)(HeaderToolbarContainer) as any) as any);

export {
    HeaderToolbar,
};
