package com.travel.intellitravel_pioneer.vo.user;

import com.travel.intellitravel_pioneer.po.UserHealthPO;
import lombok.Data;

@Data
public class UserHealthVO {

    private Integer id;
    private Integer userId;
    private String username;
    private String sex;
    private Double height;
    private Double weight;
    private Integer age;
    private Integer exerciseDaysPerWeek;
    private Integer combatPower;

    public UserHealthPO toPO() {
        UserHealthPO po = new UserHealthPO();
        po.setId(this.id);
        po.setUserId(this.userId);
        po.setUsername(this.username);
        po.setSex(this.sex);
        po.setHeight(this.height);
        po.setWeight(this.weight);
        po.setAge(this.age);
        po.setExerciseDaysPerWeek(this.exerciseDaysPerWeek);
        po.setCombatPower(this.combatPower);
        return po;
    }
}
