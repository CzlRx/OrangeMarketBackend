package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.AddCartItemRequest;
import com.czlr.orangemarketbackend.entity.dto.CartDTO;
import com.czlr.orangemarketbackend.entity.dto.CartItemDTO;
import com.czlr.orangemarketbackend.entity.dto.CartMergeRequest;
import com.czlr.orangemarketbackend.entity.dto.CartSelectionRequest;
import com.czlr.orangemarketbackend.entity.dto.UpdateCartItemRequest;
import com.czlr.orangemarketbackend.service.CartService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * 获取当前登录用户的购物车，并返回商品信息、选中数量和金额汇总。
     */
    @GetMapping
    public Result<CartDTO> getCart(@RequestAttribute("userId") Long userId) {
        return Result.success(cartService.getCart(userId));
    }

    /**
     * 向当前登录用户的购物车添加商品；重复商品会累加数量。
     */
    @PostMapping("/items")
    public Result<CartItemDTO> addItem(
            @RequestAttribute("userId") Long userId,
            @RequestBody AddCartItemRequest request) {
        return Result.success(cartService.addItem(userId, request));
    }

    /**
     * 修改当前用户购物车商品的数量或选中状态。
     */
    @PatchMapping("/items/{cartItemId}")
    public Result<CartItemDTO> updateItem(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long cartItemId,
            @RequestBody UpdateCartItemRequest request) {
        return Result.success(cartService.updateItem(userId, cartItemId, request));
    }

    /**
     * 删除当前用户购物车中的指定商品。
     */
    @DeleteMapping("/items/{cartItemId}")
    public Result<Void> deleteItem(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long cartItemId) {
        cartService.deleteItem(userId, cartItemId);
        return Result.success();
    }

    /**
     * 将当前用户购物车中的所有商品统一设置为选中或未选中。
     */
    @PutMapping("/items/selection")
    public Result<Void> updateSelection(
            @RequestAttribute("userId") Long userId,
            @RequestBody CartSelectionRequest request) {
        cartService.updateSelection(userId, request == null ? null : request.getSelected());
        return Result.success();
    }

    /**
     * 删除当前用户购物车中所有已选中的商品。
     */
    @DeleteMapping("/items/selected")
    public Result<Void> deleteSelectedItems(@RequestAttribute("userId") Long userId) {
        cartService.deleteSelectedItems(userId);
        return Result.success();
    }

    /**
     * 合并游客本地购物车；服务端会按商品合并数量并重新校验商品状态和库存。
     */
    @PostMapping("/merge")
    public Result<CartDTO> merge(
            @RequestAttribute("userId") Long userId,
            @RequestBody CartMergeRequest request) {
        return Result.success(cartService.merge(userId, request));
    }
}
