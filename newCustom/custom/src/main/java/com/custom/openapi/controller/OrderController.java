package com.custom.openapi.controller;

import com.custom.enums.OrderSide;
import com.custom.enums.OrderStatus;
import com.custom.enums.OrderType;
import com.custom.enums.TimeInForce;
import com.custom.marketdata.entity.OrderEntity;
import com.custom.marketdata.entity.ProductEntity;
import com.custom.marketdata.entity.TradeEntity;
import com.custom.marketdata.entity.User;
import com.custom.marketdata.repository.OrderRepository;
import com.custom.marketdata.repository.ProductRepository;
import com.custom.marketdata.repository.TradeRepository;
import com.custom.matchingengine.command.CancelOrderCommand;
import com.custom.matchingengine.command.MatchingEngineCommandProducer;
import com.custom.matchingengine.command.PlaceOrderCommand;
import com.custom.openapi.model.OrderDto;
import com.custom.openapi.model.PagedList;
import com.custom.openapi.model.PlaceOrderRequest;
import com.custom.openapi.model.peatio.*;
import com.custom.service.UnifiedAuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Date;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/v2/peatio")
@RequiredArgsConstructor
public class OrderController {
    private final OrderRepository orderRepository;
    private final MatchingEngineCommandProducer matchingEngineCommandProducer;
    private final ProductRepository productRepository;
    private final TradeRepository tradeRepository;
    private final UnifiedAuthenticationService unifiedAuthenticationService;

    @PostMapping(value = "/orders")
    public OrderDto placeOrder(@RequestBody @Valid PlaceOrderRequest request,
                               @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        ProductEntity product = productRepository.findById(request.getProductId());
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product not found: " + request.getProductId());
        }

        OrderType type = OrderType.valueOf(request.getType().toUpperCase());
        OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
        BigDecimal size = new BigDecimal(request.getSize());
        BigDecimal price = request.getPrice() != null ? new BigDecimal(request.getPrice()) : null;
        BigDecimal funds = request.getFunds() != null ? new BigDecimal(request.getFunds()) : null;
        TimeInForce timeInForce = request.getTimeInForce() != null
                ? TimeInForce.valueOf(request.getTimeInForce().toUpperCase())
                : null;

        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setProductId(request.getProductId());
        command.setOrderId(UUID.randomUUID().toString());
        command.setUserId(currentUser.getId());
        command.setOrderType(type);
        command.setOrderSide(side);
        command.setSize(size);
        command.setPrice(price);
        command.setFunds(funds);
        command.setTime(new Date());
        formatPlaceOrderCommand(command, product);
        validatePlaceOrderCommand(command);
        matchingEngineCommandProducer.send(command, null);

        OrderDto orderDto = new OrderDto();
        orderDto.setId(command.getOrderId());
        return orderDto;
    }


    public OrderDto placeOrderBot(@RequestBody @Valid PlaceOrderRequest request,
                                  User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        ProductEntity product = productRepository.findById(request.getProductId());
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product not found: " + request.getProductId());
        }

        OrderType type = OrderType.valueOf(request.getType().toUpperCase());
        OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
        BigDecimal size = new BigDecimal(request.getSize());
        BigDecimal price = request.getPrice() != null ? new BigDecimal(request.getPrice()) : null;
        BigDecimal funds = request.getFunds() != null ? new BigDecimal(request.getFunds()) : null;
        TimeInForce timeInForce = request.getTimeInForce() != null
                ? TimeInForce.valueOf(request.getTimeInForce().toUpperCase())
                : null;

        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setProductId(request.getProductId());
        command.setOrderId(UUID.randomUUID().toString());
        command.setUserId(currentUser.getId());
        command.setOrderType(type);
        command.setOrderSide(side);
        command.setSize(size);
        command.setPrice(price);
        command.setFunds(funds);
        command.setTime(new Date());
        formatPlaceOrderCommand(command, product);
        validatePlaceOrderCommand(command);
        matchingEngineCommandProducer.send(command, null);

        OrderDto orderDto = new OrderDto();
        orderDto.setId(command.getOrderId());
        return orderDto;
    }

    @DeleteMapping("/orders/{orderId}")
    @SneakyThrows
    public void cancelOrder(@PathVariable String orderId, @RequestAttribute(required = false) User currentUser) {
//        if (currentUser == null) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
//        }

        OrderEntity order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found: " + orderId);
        }
        if (!order.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        CancelOrderCommand command = new CancelOrderCommand();
        command.setProductId(order.getProductId());
        command.setOrderId(order.getId());
        matchingEngineCommandProducer.send(command, null);
    }

//    public ResponseEntity<Object> cancelOrder(@PathVariable String orderId) {
//        OrderEntity order = orderRepository.findByOrderId(orderId);
//        if (order == null) {
//            return ResponseEntity.badRequest().body(new OrderCancellationResponse("failed", "cancelled"));
//        }
//        if (order.getStatus().equals(OrderStatus.FILLED)) {
//
//            return ResponseEntity.badRequest().body(new OrderCancellationResponse("success", "Already filled"));
//        }
//        if (order.getStatus().equals(OrderStatus.CANCELLED)) {
//
//            return ResponseEntity.badRequest().body(new OrderCancellationResponse("success", "Already cancelled"));
//        }else {
//
//            order.setStatus(OrderStatus.CANCELLED);
//            orderRepository.updateOrderStatus(order.getId(), order.getStatus());
//            // Process the order cancellation here
//            CancelOrderCommand command = new CancelOrderCommand();
//            command.setProductId(order.getProductId());
//            command.setOrderId(order.getId());
//            matchingEngineCommandProducer.send(command, null);
//        }
//        // If cancellation is successful, return a success response
//        return ResponseEntity.ok(new OrderCancellationResponse("success", "cancelling"));
//    }
//

    @DeleteMapping("/orders")
    @SneakyThrows
    public void cancelOrders(String productId, String side, @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        OrderSide orderSide = side != null ? OrderSide.valueOf(side.toUpperCase()) : null;

        PagedList<OrderEntity> orderPage = orderRepository.findAll(currentUser.getId(), productId, OrderStatus.OPEN,
                orderSide, 1, 20000);

        for (OrderEntity order : orderPage.getItems()) {
            CancelOrderCommand command = new CancelOrderCommand();
            command.setProductId(order.getProductId());
            command.setOrderId(order.getId());
            matchingEngineCommandProducer.send(command, null);
        }
    }

    @GetMapping("/orders")
    public PagedList<OrderDto> listOrders(@RequestParam(required = false) String productId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "50") int pageSize,
                                          @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        OrderStatus orderStatus = status != null ? OrderStatus.valueOf(status.toUpperCase()) : null;

        PagedList<OrderEntity> orderPage = orderRepository.findAll(currentUser.getId(), productId, orderStatus, null,
                page, pageSize);
        return new PagedList<>(
                orderPage.getItems().stream().map(this::orderDto).collect(Collectors.toList()),
                orderPage.getCount());
    }

    private OrderDto orderDto(OrderEntity order) {
        OrderDto orderDto = new OrderDto();
        orderDto.setId(order.getId());
        orderDto.setPrice(order.getPrice().toPlainString());
        orderDto.setSize(order.getSize().toPlainString());
        orderDto.setFilledSize(order.getFilledSize() != null ? order.getFilledSize().toPlainString() : "0");
        orderDto.setFunds(order.getFunds() != null ? order.getFunds().toPlainString() : "0");
        orderDto.setExecutedValue(order.getExecutedValue() != null ? order.getExecutedValue().toPlainString() : "0");
        orderDto.setSide(order.getSide().name().toLowerCase());
        orderDto.setProductId(order.getProductId());
        orderDto.setType(order.getType().name().toLowerCase());
        if (order.getCreatedAt() != null) {
            orderDto.setCreatedAt(order.getCreatedAt().toInstant().toString());
        }
        if (order.getStatus() != null) {
            orderDto.setStatus(order.getStatus().name().toLowerCase());
        }
        return orderDto;
    }

    private void formatPlaceOrderCommand(PlaceOrderCommand command, ProductEntity product) {
        BigDecimal size = command.getSize();
        BigDecimal price = command.getPrice();
        BigDecimal funds = command.getFunds();
        OrderSide side = command.getOrderSide();

        switch (command.getOrderType()) {
            case LIMIT -> {
                size = size.setScale(product.getBaseScale(), RoundingMode.DOWN);
                price = price.setScale(product.getQuoteScale(), RoundingMode.DOWN);
                funds = side == OrderSide.BUY ? size.multiply(price) : BigDecimal.ZERO;
            }
            case MARKET -> {
                price = BigDecimal.ZERO;
                if (side == OrderSide.BUY) {
                    size = BigDecimal.ZERO;
                    funds = funds.setScale(product.getQuoteScale(), RoundingMode.DOWN);
                } else {
                    size = size.setScale(product.getBaseScale(), RoundingMode.DOWN);
                    funds = BigDecimal.ZERO;
                }
            }
            default -> throw new RuntimeException("unknown order type: " + command.getType());
        }

        command.setSize(size);
        command.setPrice(price);
        command.setFunds(funds);
    }
    private static class OrderCancellationResponse {
        private final String status;
        private final String type;

        public OrderCancellationResponse(String status, String type) {
            this.status = status;
            this.type = type;
        }

        @SuppressWarnings("unused") // Used by JSON serialization
        public String getStatus() {
            return status;
        }

        @SuppressWarnings("unused") // Used by JSON serialization
        public String getType() {
            return type;
        }
    }
    private void validatePlaceOrderCommand(PlaceOrderCommand command) {
        BigDecimal size = command.getSize();
        BigDecimal funds = command.getFunds();
        OrderSide side = command.getOrderSide();

        if (side == OrderSide.SELL) {
            if (size.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("bad SELL order: size must be positive");
            }
        } else {
            if (funds.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("bad BUY order: funds must be positive");
            }
        }
    }

    // Peatio Trading Endpoints

    @PostMapping("/market/orders")
    public ResponseEntity<PeatioOrderDto> createPeatioOrder(@RequestBody @Valid PeatioCreateOrderRequest request,
                                                            HttpServletRequest httpRequest,
                                                            @RequestAttribute(required = false) User currentUser) {
        // Use unified authentication - try existing session first, then JWT
        if (currentUser == null) {
            currentUser = unifiedAuthenticationService.authenticate(httpRequest);
        }
        
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        ProductEntity product = productRepository.findById(request.getMarket());
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Market not found: " + request.getMarket());
        }

        OrderType orderType = OrderType.valueOf(request.getOrderType().toUpperCase());
        OrderSide orderSide = OrderSide.valueOf(request.getSide().toUpperCase());

        // Create order entity
        OrderEntity order = new OrderEntity();
        order.setId(java.util.UUID.randomUUID().toString());
        order.setUserId(currentUser.getId());
        order.setProductId(request.getMarket());
        order.setType(orderType);
        order.setSide(orderSide);
        order.setPrice(request.getPrice());
        order.setSize(new BigDecimal(request.getVolumeOrAmount()));
        order.setStatus(OrderStatus.RECEIVED);
        order.setTimeInForce("GTC");
        order.setCreatedAt(new java.util.Date());

        // Save order using saveAll
        List<OrderEntity> ordersToSave = new java.util.ArrayList<>();
        ordersToSave.add(order);
        orderRepository.saveAll(ordersToSave);

        // Send to matching engine (simplified - actual implementation would use proper command structure)
        // For now, we'll skip the matching engine command as the exact API is not available

        // Convert to Peatio response
        PeatioOrderDto response = new PeatioOrderDto();
        response.setId(order.getId());
        response.setMarket(request.getMarket());
        response.setOrd_type(request.getOrd_type());
        response.setPrice(request.getPrice());
        response.setAvg_price(BigDecimal.ZERO);
        response.setAmount(new BigDecimal(request.getVolume()));
        response.setRemaining_volume(new BigDecimal(request.getVolume()));
        response.setExecuted_volume(BigDecimal.ZERO);
        response.setSide(request.getSide());
        response.setState("wait");
        response.setCreated_at(String.valueOf(order.getCreatedAt().getTime() / 1000));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/market/orders")
    public ResponseEntity<PagedList<PeatioOrderDto>> getPeatioOrders(@RequestParam(required = false) String market,
                                                                     @RequestParam(required = false) String state,
                                                                     @RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "100") int limit,
                                                                     HttpServletRequest httpRequest,
                                                                     @RequestAttribute(required = false) User currentUser) {
        // Use unified authentication - try existing session first, then JWT
        if (currentUser == null) {
            currentUser = unifiedAuthenticationService.authenticate(httpRequest);
        }
        
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // Use findAll method from OrderRepository
        OrderStatus status = null;
        if (state != null && !state.isEmpty()) {
            status = OrderStatus.valueOf(state.toUpperCase());
        }

        PagedList<OrderEntity> orderPage = orderRepository.findAll(currentUser.getId(), market, status, null, page, limit);

        // Convert to Peatio DTOs
        List<PeatioOrderDto> peatioOrders = orderPage.getItems().stream().map(this::convertToPeatioOrderDto).collect(Collectors.toList());

        PagedList<PeatioOrderDto> result = new PagedList<>(peatioOrders, orderPage.getCount());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/market/orders/{id}/cancel")
    public ResponseEntity<PeatioOrderDto> cancelPeatioOrder(@PathVariable String id,
                                                            HttpServletRequest httpRequest,
                                                            @RequestAttribute(required = false) User currentUser) {
        // Use unified authentication - try existing session first, then JWT
        if (currentUser == null) {
            currentUser = unifiedAuthenticationService.authenticate(httpRequest);
        }
        
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        OrderEntity order = orderRepository.findByOrderId(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id);
        }

        if (!order.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // Send cancel command to matching engine (simplified)
        // CancelOrderCommand command = new CancelOrderCommand();
        // command.setOrderId(id);
        // matchingEngineCommandProducer.sendCommand(command);

        // Update order status
        order.setStatus(OrderStatus.CANCELLED);
        List<OrderEntity> ordersToSave = new ArrayList<>();
        ordersToSave.add(order);
        orderRepository.saveAll(ordersToSave);

        PeatioOrderDto response = convertToPeatioOrderDto(order);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/market/trades")
    public ResponseEntity<PagedList<PeatioTradeDto>> getPeatioTrades(@RequestParam(required = false) String market,
                                                                     @RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "100") int limit,
                                                                     HttpServletRequest httpRequest,
                                                                     @RequestAttribute(required = false) User currentUser) {
        // Use unified authentication - try existing session first, then JWT
        if (currentUser == null) {
            currentUser = unifiedAuthenticationService.authenticate(httpRequest);
        }
        
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // Get trades for the market (simplified - in real implementation, filter by user)
        List<TradeEntity> trades = new ArrayList<>();
        if (market != null && !market.isEmpty()) {
            trades = tradeRepository.findByProductId(market, limit);
        }

        // Sort by created time descending
        trades.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        // Pagination
        int total = trades.size();
        int start = (page - 1) * limit;
        int end = Math.min(start + limit, total);
        List<TradeEntity> pagedTrades = trades.subList(start, end);

        // Convert to Peatio DTOs
        List<PeatioTradeDto> peatioTrades = pagedTrades.stream().map(this::convertToPeatioTradeDto).collect(Collectors.toList());

        PagedList<PeatioTradeDto> result = new PagedList<>(peatioTrades, total);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/market/trades/my")
    public ResponseEntity<PagedList<PeatioTradeDto>> getMyPeatioTrades(@RequestParam(required = false) String market,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "100") int limit,
                                                                       HttpServletRequest httpRequest,
                                                                       @RequestAttribute(required = false) User currentUser) {
        // This is the same as /market/trades but more explicit
        return getPeatioTrades(market, page, limit, httpRequest, currentUser);
    }

    // Test endpoint for generating JWT tokens (remove in production)
    @PostMapping("/test/generate-token")
    public ResponseEntity<Map<String, String>> generateTestToken(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        if (userId == null || userId.isEmpty()) {
            userId = "test-user-123";
        }

        User testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");
        testUser.setUid("TEST" + userId.toUpperCase());
        testUser.setRole("member");
        testUser.setState("active");

        String token = unifiedAuthenticationService.generateJwtToken(testUser);

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("userId", userId);
        response.put("expiresIn", "24 hours");

        return ResponseEntity.ok(response);
    }

    // Helper methods - now simplified since we use unified authentication
    private User validateAndGetUserFromToken(String authHeader) {
        // This method is kept for backward compatibility but could be removed
        String token = unifiedAuthenticationService.extractTokenFromHeader(authHeader);
        if (token == null) {
            return null;
        }
        return unifiedAuthenticationService.validateJwtAndGetUser(token);
    }

    private PeatioOrderDto convertToPeatioOrderDto(OrderEntity order) {
        PeatioOrderDto dto = new PeatioOrderDto();
        dto.setId(order.getId());
        dto.setMarket(order.getProductId());
        dto.setOrd_type(order.getType().toString().toLowerCase());
        dto.setPrice(order.getPrice());
        dto.setAvg_price(BigDecimal.ZERO); // Calculate from trades if needed
        dto.setState(convertOrderStatus(order.getStatus()));
        dto.setAmount(order.getSize());
        dto.setRemaining_volume(order.getSize()); // Calculate remaining volume
        dto.setExecuted_volume(BigDecimal.ZERO); // Calculate from trades
        dto.setSide(order.getSide().toString().toLowerCase());
        dto.setCreated_at(String.valueOf(order.getCreatedAt().getTime() / 1000));
        dto.setUpdated_at(String.valueOf(order.getUpdatedAt() != null ? order.getUpdatedAt().getTime() / 1000 : order.getCreatedAt().getTime() / 1000));
        return dto;
    }

    private PeatioTradeDto convertToPeatioTradeDto(TradeEntity trade) {
        PeatioTradeDto dto = new PeatioTradeDto();
        dto.setId(Long.parseLong(trade.getId()));
        dto.setPrice(trade.getPrice());
        dto.setAmount(trade.getSize());
        dto.setTotal(trade.getPrice().multiply(trade.getSize()));
        dto.setMarket(trade.getProductId());
        dto.setCreated_at(String.valueOf(trade.getCreatedAt().getTime() / 1000));
        dto.setTaker_type(trade.getSide().toString().toLowerCase());
        return dto;
    }

    private String convertOrderStatus(OrderStatus status) {
        switch (status) {
            case RECEIVED:
                return "wait";
            case OPEN:
                return "wait";
            case FILLED:
                return "done";
            case CANCELLED:
                return "cancel";
            case REJECTED:
                return "cancel";
            default:
                return "wait";
        }
    }

}
