package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserHealthPO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.UserHealthRepository;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.service.UserHealthService;
import com.travel.intellitravel_pioneer.vo.user.UserHealthVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserHealthServiceImpl implements UserHealthService {

    @Autowired
    private UserHealthRepository userHealthRepository;

    @Autowired
    private UserRepository userRepository;

    private static final int DEFAULT_INITIAL_POWER = 1000;

    @Override
    public UserHealthVO getHealthInformation(String username) {
        UserPO userPO = userRepository.findByUsername(username);
        if (userPO == null) {
            throw IntellitravelException.userNotFound();
        }

        UserHealthPO healthPO = userHealthRepository.findByUserId(userPO.getId());
        if (healthPO == null) {
            // 返回默认空的值，带上已知的 userId 和 username
            UserHealthVO defaultVO = new UserHealthVO();
            defaultVO.setUserId(userPO.getId());
            defaultVO.setUsername(userPO.getUsername());
            return defaultVO; /* 没有健康信息则返回不带数据的对象，由前端引导去填 */ 
        }

        return healthPO.toVO();
    }

    @Override
    public String saveOrUpdateHealthInformation(UserHealthVO userHealthVO) {
        if (userHealthVO.getUsername() == null) {
            return "Username is missing in the request.";
        }

        int calculatedCombatPower = calculateCombatPower(userHealthVO.toPO());
        userHealthVO.setCombatPower(calculatedCombatPower);
        
        UserPO userPO = userRepository.findByUsername(userHealthVO.getUsername());
        if (userPO == null) {
            throw IntellitravelException.userNotFound();
        }

        UserHealthPO existingHealthPO = userHealthRepository.findByUserId(userPO.getId());

        if (existingHealthPO != null) {
            // Update existings
            existingHealthPO.setSex(userHealthVO.getSex());
            existingHealthPO.setUsername(userPO.getUsername()); 
            existingHealthPO.setHeight(userHealthVO.getHeight());
            existingHealthPO.setWeight(userHealthVO.getWeight());
            existingHealthPO.setAge(userHealthVO.getAge());
            existingHealthPO.setExerciseDaysPerWeek(userHealthVO.getExerciseDaysPerWeek());
            existingHealthPO.setCombatPower(calculatedCombatPower);
            userHealthRepository.save(existingHealthPO);
        } else {
            // Validate the VO doesn't have mismatched info on accident, though normally trust the user query
            UserHealthPO newHealthPO = userHealthVO.toPO();
            newHealthPO.setId(null); // Let the DB autoincrement ID
            newHealthPO.setUserId(userPO.getId()); // forcefully bind the matching user.id
            newHealthPO.setUsername(userPO.getUsername());
            userHealthRepository.save(newHealthPO);
        }

        return "Success";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int calculateCombatPower(UserHealthPO userHealthPO) {
        if (userHealthPO == null) {
            return DEFAULT_INITIAL_POWER;
        }

        double sexScore = calculateSexScore(userHealthPO.getSex());
        double bmiScore = calculateBmiScore(userHealthPO.getHeight(), userHealthPO.getWeight());
        double ageScore = calculateAgeScore(userHealthPO.getAge());
        double exerciseScore = calculateExerciseScore(userHealthPO.getExerciseDaysPerWeek());

        double rawPower = 250 + sexScore * 1 + bmiScore * 2 + ageScore * 2 + exerciseScore * 3;
        return clamp((int) Math.round(rawPower), 300, 1500);
    }

    private double calculateSexScore(String sex) {
        if (sex == null || sex.isBlank()) {
            return 75;
        }

        String normalizedSex = sex.trim().toLowerCase();
        if ("male".equals(normalizedSex) || "man".equals(normalizedSex) || "m".equals(normalizedSex) || "男".equals(sex.trim())) {
            return 85;
        }
        if ("female".equals(normalizedSex) || "woman".equals(normalizedSex) || "f".equals(normalizedSex) || "女".equals(sex.trim())) {
            return 80;
        }
        return 75;
    }

    private double calculateBmiScore(Double height, Double weight) {
        if (height == null || height <= 0 || weight == null || weight <= 0) {
            return 80;
        }

        double heightMeter = height > 3 ? height / 100.0 : height;
        if (heightMeter <= 0) {
            return 80;
        }
        double bmi = weight / (heightMeter * heightMeter);
        double rawScore = 100 - Math.abs(bmi - 22) * 8;
        return clamp(rawScore, 40, 100);
    }

    private double calculateAgeScore(Integer age) {
        if (age == null || age <= 0) {
            return 70;
        }
        double penalty = Math.max(0, age - 25) * 1.2 + Math.max(0, 18 - age) * 0.5;
        return clamp(100 - penalty, 50, 100);
    }

    private double calculateExerciseScore(Integer exerciseDaysPerWeek) {
        if (exerciseDaysPerWeek == null) {
            return 60;
        }
        int safeDays = clamp(exerciseDaysPerWeek, 0, 7);
        return clamp(20 + safeDays * 12.0, 20, 100);
    }
}
