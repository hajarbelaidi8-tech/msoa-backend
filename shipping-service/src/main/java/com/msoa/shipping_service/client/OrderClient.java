
package com.msoa.shipping_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderClient {

    private final RestTemplate restTemplate;

    public OrderClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public void verifyOrderIsPaid(String orderId) {

        String url = "http://localhost:8084/api/orders/" + orderId;

        try {

            JsonNode order = restTemplate.getForObject(
                    url,
                    JsonNode.class
            );

            if (order == null || !order.has("status")
                    || order.get("status").isNull()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Invalid response from Orders Service"
                );
            }

            String status = order.get("status").asText();

            if (!"PAID".equalsIgnoreCase(status)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Order is not paid"
                );
            }

        } catch (HttpStatusCodeException e) {

            if (e.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Orders Service returned an error"
            );

        } catch (ResourceAccessException e) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Orders Service is unavailable"
            );

        } catch (RestClientException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Communication error with Orders Service"
            );
        }
    }
}
