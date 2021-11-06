package numb.hotswap;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author qingzhu
 * @Date 2021/8/3
 * @Desciption
 */
@Data
@AllArgsConstructor
public class CompliedInfo {

    private String fullClassName;

    private byte[] byteCode;

}
