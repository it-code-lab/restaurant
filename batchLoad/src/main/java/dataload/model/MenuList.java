package dataload.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@DynamoDBDocument
public class MenuList {

    // DynamoDBAttribute annotation to mark the items field for persistence
    @DynamoDBAttribute
    private List<Menu> items;
}
