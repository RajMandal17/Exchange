package com.custom.marketdata.manager;

import com.custom.marketdata.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductManager {
    private final ProductRepository productRepository;

}
