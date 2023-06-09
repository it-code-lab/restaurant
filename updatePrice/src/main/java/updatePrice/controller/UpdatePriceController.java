package updatePrice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import updatePrice.model.*;
import updatePrice.repository.RestaurantRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/food/api/v1/admin")
public class UpdatePriceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdatePriceController.class);

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public RestaurantRepository getRestaurantRepository() {
        return restaurantRepository;
    }

    public void setRestaurantRepository(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RabbitTemplate getRabbitTemplate() {
        return rabbitTemplate;
    }

    private final RabbitTemplate rabbitTemplate;

    public UpdatePriceController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Updates the price for a specific menu item in a restaurant.
     *
     * @param restaurantName     The name of the restaurant.
     * @param priceUpdateRequest The name of the menu item.
     * @return ResponseEntity containing the status of the price update or error message.
     */
    @PostMapping("/update-price/menu/{restaurantName}")
    public ResponseEntity<String> updatePrice(
            @PathVariable String restaurantName, @RequestBody PriceUpdateRequest priceUpdateRequest
    ) {

        try {
            String menuItemName = priceUpdateRequest.getMenuItemName();
            String newPrice = priceUpdateRequest.getNewPrice();

            LOGGER.info("Updating price for item: {} in restaurant: {}", menuItemName, restaurantName);

            Restaurant existingRestaurant = restaurantRepository.getRestaurantByRestaurantName(restaurantName);
            if (existingRestaurant == null) {
                LOGGER.warn("Restaurant not found: {}", restaurantName);
                return ResponseEntity.badRequest().body("Restaurant not found");
            }

            if (!isValidValue(menuItemName)) {
                LOGGER.warn("Invalid item name: {}", menuItemName);
                return ResponseEntity.badRequest().body("Item name " + menuItemName + " is invalid");
            }

            Pattern pattern = Pattern.compile("\\d+(\\.\\d+)?");
            Matcher matcher = pattern.matcher(newPrice);
            if (!matcher.matches()) {
                LOGGER.warn("Non-numeric price: {} for item: {} in restaurant: {}", newPrice, menuItemName, restaurantName);
                return ResponseEntity.badRequest().body("Price " + newPrice + " of item " + menuItemName + " under restaurant " + restaurantName + " is non-numeric");
            }

            double price = Double.parseDouble(newPrice);
            if (price < 100 || price > 200) {
                LOGGER.warn("Invalid price range: {} for item: {} in restaurant: {}", newPrice, menuItemName, restaurantName);
                return ResponseEntity.badRequest().body("Price " + newPrice + " of item " + menuItemName + " under restaurant " + restaurantName + " is outside allowed range 100-200");
            }

            MenuList menuList = existingRestaurant.getMenuList();
            List<Menu> items = menuList.getItems();

            AtomicBoolean itemFound = new AtomicBoolean(false);
            items.replaceAll(menu -> {
                if (menu.getItemName().equals(menuItemName)) {
                    menu.setPrice(newPrice);
                    itemFound.set(true);
                }
                return menu;
            });

            if (!itemFound.get()) {
                LOGGER.warn("Menu item not found: {} in restaurant: {}", menuItemName, restaurantName);
                return ResponseEntity.badRequest().body("Menu item " + menuItemName + " under restaurant " + restaurantName + " is not found");
            }

            menuList.setItems(items);
            existingRestaurant.setMenuList(menuList);

            existingRestaurant.setUpdatedAt(String.valueOf(LocalDateTime.now()));
            restaurantRepository.saveRestaurant(existingRestaurant);
            LOGGER.info("Price updated successfully for item: {} in restaurant: {}", menuItemName, restaurantName);

            PriceUpdateCommand priceUpdateCommand = new PriceUpdateCommand();
            priceUpdateCommand.setRestaurantName(existingRestaurant.getRestaurantName());
            priceUpdateCommand.setAddress(existingRestaurant.getAddress());
            priceUpdateCommand.setMenuList(existingRestaurant.getMenuList());
            priceUpdateCommand.setUpdatedAt(existingRestaurant.getUpdatedAt());
            priceUpdateCommand.setCreatedAt(existingRestaurant.getCreatedAt());


            String restaurantJson = objectMapper.writeValueAsString(priceUpdateCommand);
            Message message = MessageBuilder
                    .withBody(restaurantJson.getBytes())
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .build();
            this.rabbitTemplate.convertAndSend("priceupdate-command", message);

            return ResponseEntity.ok("Price updated successfully");
        } catch (Exception e) {
            LOGGER.error("Error occurred while updating price for item: {} in restaurant: {}", priceUpdateRequest.getMenuItemName(), restaurantName, e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    public static boolean isValidValue(String value) {
        for (Menu.ItemName itemName : Menu.ItemName.values()) {
            if (itemName.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }

}
