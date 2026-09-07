package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import com.czlr.orangemarketbackend.common.enums.ReviewStatus;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.entity.dto.PendingReviewItemDTO;
import com.czlr.orangemarketbackend.entity.dto.ReviewItemRequest;
import com.czlr.orangemarketbackend.entity.dto.ReviewSubmissionResultDTO;
import com.czlr.orangemarketbackend.entity.dto.SubmitReviewsRequest;
import com.czlr.orangemarketbackend.entity.po.Order;
import com.czlr.orangemarketbackend.entity.po.OrderItem;
import com.czlr.orangemarketbackend.entity.po.ProductReview;
import com.czlr.orangemarketbackend.mapper.OrderItemMapper;
import com.czlr.orangemarketbackend.mapper.OrderMapper;
import com.czlr.orangemarketbackend.mapper.ProductReviewMapper;
import com.czlr.orangemarketbackend.mapper.ProductMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductReviewMapper productReviewMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;

    public ReviewService(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            ProductReviewMapper productReviewMapper,
            ProductMapper productMapper,
            ProductService productService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productReviewMapper = productReviewMapper;
        this.productMapper = productMapper;
        this.productService = productService;
    }

    public List<PendingReviewItemDTO> getPendingReviews(Long userId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatus.PENDING_REVIEW)
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId));
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<OrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getOrderId, orderIds)
                .orderByAsc(OrderItem::getCreatedAt)
                .orderByAsc(OrderItem::getId));
        if (orderItems.isEmpty()) {
            return List.of();
        }

        List<Long> orderItemIds = orderItems.stream().map(OrderItem::getId).toList();
        Set<Long> reviewedItemIds = productReviewMapper.selectList(new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getUserId, userId)
                        .in(ProductReview::getOrderItemId, orderItemIds))
                .stream()
                .map(ProductReview::getOrderItemId)
                .collect(Collectors.toSet());

        Map<Long, Order> ordersById = orders.stream()
                .collect(Collectors.toMap(Order::getId, order -> order, (first, second) -> first,
                        LinkedHashMap::new));
        Map<Long, List<OrderItem>> itemsByOrderId = orderItems.stream()
                .filter(item -> item.getProductId() != null)
                .filter(item -> !reviewedItemIds.contains(item.getId()))
                .collect(Collectors.groupingBy(
                        OrderItem::getOrderId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return orders.stream()
                .flatMap(order -> itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList())
                        .stream()
                        .map(item -> toPendingReviewItem(ordersById.get(order.getId()), item)))
                .toList();
    }

    @Transactional
    public ReviewSubmissionResultDTO submitReviews(
            Long userId, Long orderId, SubmitReviewsRequest request) {
        List<ReviewCommand> commands = validateRequest(request);
        Order order = getOwnedPendingReviewOrder(userId, orderId);
        Map<Long, OrderItem> orderItemsById = getOrderItems(orderId, commands);
        validateOrderItems(commands, orderItemsById);

        List<Long> orderItemIds = commands.stream().map(ReviewCommand::orderItemId).toList();
        List<ProductReview> existingReviews = productReviewMapper.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getUserId, userId)
                        .in(ProductReview::getOrderItemId, orderItemIds));
        if (!existingReviews.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT, "订单商品不能重复评价");
        }

        List<ProductReview> reviews = commands.stream()
                .map(command -> toProductReview(userId, command))
                .toList();
        try {
            reviews.forEach(productReviewMapper::insert);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.CONFLICT, "订单商品不能重复评价");
        }

        Map<Long, RatingAggregate> aggregates = reviews.stream()
                .collect(Collectors.toMap(
                        ProductReview::getProductId,
                        review -> new RatingAggregate(
                                BigDecimal.valueOf(review.getRating()), 1),
                        RatingAggregate::merge,
                        LinkedHashMap::new));
        for (Map.Entry<Long, RatingAggregate> entry : aggregates.entrySet()) {
            RatingAggregate aggregate = entry.getValue();
            int updated = productMapper.updateReviewStats(
                    entry.getKey(), aggregate.ratingSum(), aggregate.reviewCount());
            if (updated == 0) {
                throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
            }
            productService.invalidateProductCaches(entry.getKey());
        }

        Completion completion = completeOrderIfNecessary(userId, order, orderId);
        return new ReviewSubmissionResultDTO(
                String.valueOf(orderId),
                order.getOrderNo(),
                completion.status(),
                reviews.stream().map(review -> String.valueOf(review.getId())).toList(),
                completion.completedAt());
    }

    private List<ReviewCommand> validateRequest(SubmitReviewsRequest request) {
        if (request == null || request.getReviews() == null || request.getReviews().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "reviews 不能为空");
        }

        Set<Long> orderItemIds = new LinkedHashSet<>();
        return request.getReviews().stream()
                .map(this::toReviewCommand)
                .peek(command -> {
                    if (!orderItemIds.add(command.orderItemId())) {
                        throw new BusinessException(ResultCode.BAD_REQUEST, "orderItemId 不能重复");
                    }
                })
                .toList();
    }

    private ReviewCommand toReviewCommand(ReviewItemRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "评价内容不能为空");
        }
        Long orderItemId = parseId(request.getOrderItemId(), "orderItemId");
        Long productId = parseId(request.getProductId(), "productId");
        Integer rating = request.getRating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "rating 必须在 1 到 5 之间");
        }
        String content = request.getContent() == null ? null : request.getContent().trim();
        if (content == null || content.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "content 不能为空");
        }
        return new ReviewCommand(
                orderItemId,
                productId,
                rating,
                content,
                Boolean.TRUE.equals(request.getAnonymous()));
    }

    private Order getOwnedPendingReviewOrder(Long userId, Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "orderId 参数错误");
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_REVIEW) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "当前订单状态不允许评价");
        }
        return order;
    }

    private Map<Long, OrderItem> getOrderItems(Long orderId, List<ReviewCommand> commands) {
        List<Long> orderItemIds = commands.stream().map(ReviewCommand::orderItemId).toList();
        Map<Long, OrderItem> orderItemsById = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
                        .in(OrderItem::getId, orderItemIds))
                .stream()
                .collect(Collectors.toMap(
                        OrderItem::getId,
                        item -> item,
                        (first, second) -> first,
                        LinkedHashMap::new));
        if (orderItemsById.size() != orderItemIds.size()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单商品不存在");
        }
        return orderItemsById;
    }

    private void validateOrderItems(
            List<ReviewCommand> commands, Map<Long, OrderItem> orderItemsById) {
        for (ReviewCommand command : commands) {
            OrderItem orderItem = orderItemsById.get(command.orderItemId());
            if (!Objects.equals(orderItem.getProductId(), command.productId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "productId 与订单商品不一致");
            }
            if (orderItem.getProductId() == null) {
                throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "商品已不存在，无法评价");
            }
        }
    }

    private ProductReview toProductReview(Long userId, ReviewCommand command) {
        ProductReview review = new ProductReview();
        review.setProductId(command.productId());
        review.setOrderItemId(command.orderItemId());
        review.setUserId(userId);
        review.setRating(command.rating());
        review.setContent(command.content());
        review.setAnonymous(command.anonymous() ? 1 : 0);
        review.setStatus(ReviewStatus.VISIBLE);
        return review;
    }

    private Completion completeOrderIfNecessary(Long userId, Order order, Long orderId) {
        List<OrderItem> allOrderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
        Set<Long> allOrderItemIds = allOrderItems.stream()
                .map(OrderItem::getId)
                .collect(Collectors.toSet());
        Set<Long> reviewedItemIds = productReviewMapper.selectList(new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getUserId, userId)
                        .in(ProductReview::getOrderItemId, allOrderItemIds))
                .stream()
                .map(ProductReview::getOrderItemId)
                .collect(Collectors.toSet());
        if (allOrderItems.isEmpty() || !reviewedItemIds.containsAll(allOrderItemIds)) {
            return new Completion(OrderStatus.PENDING_REVIEW, null);
        }

        LocalDateTime completedAt = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatus.PENDING_REVIEW)
                .set(Order::getStatus, OrderStatus.COMPLETED)
                .set(Order::getCompletedAt, completedAt));
        if (updated == 0) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单状态已发生变化");
        }
        return new Completion(OrderStatus.COMPLETED, completedAt);
    }

    private PendingReviewItemDTO toPendingReviewItem(Order order, OrderItem item) {
        return new PendingReviewItemDTO(
                String.valueOf(order.getId()),
                order.getOrderNo(),
                String.valueOf(item.getId()),
                String.valueOf(item.getProductId()),
                item.getProductName(),
                item.getProductImage(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineAmount());
    }

    private Long parseId(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 不能为空");
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 参数错误");
        }
    }

    private record ReviewCommand(
            Long orderItemId,
            Long productId,
            Integer rating,
            String content,
            boolean anonymous) {
    }

    private record RatingAggregate(BigDecimal ratingSum, int reviewCount) {
        private RatingAggregate merge(RatingAggregate other) {
            return new RatingAggregate(
                    ratingSum.add(other.ratingSum()),
                    reviewCount + other.reviewCount());
        }
    }

    private record Completion(OrderStatus status, LocalDateTime completedAt) {
    }
}
