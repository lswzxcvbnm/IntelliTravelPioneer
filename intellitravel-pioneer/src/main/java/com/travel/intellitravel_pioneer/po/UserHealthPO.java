package com.travel.intellitravel_pioneer.po;

import com.travel.intellitravel_pioneer.vo.user.UserHealthVO;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_health")
public class UserHealthPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "sex")
    private String sex;

    @Column(name = "height")
    private Double height;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "age")
    private Integer age;

    @Column(name = "exercise_days_per_week")
    private Integer exerciseDaysPerWeek;

    @Column(name = "combat_power")
    private Integer combatPower;

    public UserHealthVO toVO() {
        UserHealthVO vo = new UserHealthVO();
        vo.setId(this.id);
        vo.setUserId(this.userId);
        vo.setUsername(this.username);
        vo.setSex(this.sex);
        vo.setHeight(this.height);
        vo.setWeight(this.weight);
        vo.setAge(this.age);
        vo.setExerciseDaysPerWeek(this.exerciseDaysPerWeek);
        vo.setCombatPower(this.combatPower);
        return vo;
    }
}
