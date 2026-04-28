package com.travel.intellitravel_pioneer.po;

import com.travel.intellitravel_pioneer.vo.user.UserVO;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user")
public class UserPO {

    public static final int MAX_GRADE = 60;
    public static final int BASE_EXP_PER_LEVEL = 100;

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Integer id;

    @Basic
    @Column(name = "username")
    private String username;

    @Basic
    @Column(name = "password")
    private String password;

    @Basic
    @Column(name = "name")
    private String name;

    @Basic
    @Column(name = "grade")
    private String grade;

    @Basic
    @Column(name = "experience")
    private Integer experience = 0;

    @Basic
    @Column(name = "avatar", length = 2048)
    private String avatar;

    @Basic
    @Column(name = "telephone")
    private String telephone;

    @Basic
    @Column(name = "email")
    private String email;

    @Basic
    @Column(name = "location")
    private String location;

    public UserVO toVO() {
        UserVO userVO = new UserVO();
        userVO.setId(this.id);
        userVO.setUsername(this.username);
        userVO.setName(this.name);
        userVO.setGrade(this.grade);
        userVO.setExperience(this.experience);
        userVO.setAvatar(this.avatar);
        userVO.setTelephone(this.telephone);
        userVO.setEmail(this.email);
        userVO.setLocation(this.location);
        
        int currentGrade = parseGrade(this.grade);
        int currentExp = this.experience == null ? 0 : this.experience;
        
        if (currentGrade >= MAX_GRADE) {
            userVO.setNextGradeExp(null);
            userVO.setExpToNextGrade(0);
            userVO.setProgress(1.0);
            userVO.setMaxLevelReached(true);
        } else {
            int expNeededForNextLevel = getExpNeededForLevel(currentGrade + 1);
            int expNeededForCurrentLevel = getExpNeededForLevel(currentGrade);
            int expInCurrentLevel = currentExp;
            int expToNextGrade = expNeededForNextLevel - expNeededForCurrentLevel - expInCurrentLevel;
            
            userVO.setNextGradeExp(expNeededForNextLevel);
            userVO.setExpToNextGrade(Math.max(0, expToNextGrade));
            
            int levelExpRange = expNeededForNextLevel - expNeededForCurrentLevel;
            double progress = levelExpRange > 0 ? (double) expInCurrentLevel / levelExpRange : 0.0;
            userVO.setProgress(Math.min(1.0, Math.max(0.0, progress)));
            userVO.setMaxLevelReached(false);
        }
        
        return userVO;
    }

    public static int getExpNeededForLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        return BASE_EXP_PER_LEVEL * level * (level - 1) / 2;
    }

    /**
     * Adds experience points to the user and upgrades grade when thresholds are met.
     *
     * @param expToAdd experience points to add (must be greater than 0)
     * @return LevelUpResult containing upgrade information, or null if no upgrade occurred
     */
    public LevelUpResult addExperience(int expToAdd) {
        if (expToAdd <= 0) {
            return null;
        }
        if (this.experience == null) {
            this.experience = 0;
        }
        
        int oldGrade = parseGrade(this.grade);
        
        if (oldGrade >= MAX_GRADE) {
            return null;
        }
        
        int totalExp = getExpNeededForLevel(oldGrade) + this.experience + expToAdd;
        int newGrade = calculateGradeByExp(totalExp, oldGrade);
        
        if (newGrade > oldGrade && newGrade <= MAX_GRADE) {
            int actualNewGrade = Math.min(newGrade, MAX_GRADE);
            int levelsGained = actualNewGrade - oldGrade;
            
            if (actualNewGrade >= MAX_GRADE) {
                this.grade = String.valueOf(MAX_GRADE);
                this.experience = 0;
            } else {
                this.grade = String.valueOf(actualNewGrade);
                this.experience = totalExp - getExpNeededForLevel(actualNewGrade);
            }
            
            return new LevelUpResult(oldGrade, actualNewGrade, levelsGained);
        }
        
        this.experience = totalExp - getExpNeededForLevel(oldGrade);
        return null;
    }

    public static class LevelUpResult {
        private final int oldGrade;
        private final int newGrade;
        private final int levelsGained;

        public LevelUpResult(int oldGrade, int newGrade, int levelsGained) {
            this.oldGrade = oldGrade;
            this.newGrade = newGrade;
            this.levelsGained = levelsGained;
        }

        public int getOldGrade() { return oldGrade; }
        public int getNewGrade() { return newGrade; }
        public int getLevelsGained() { return levelsGained; }
        public boolean isLevelUp() { return levelsGained > 0; }
    }

    private static int parseGrade(String grade) {
        if (grade == null) {
            return 1;
        }
        try {
            return Integer.parseInt(grade);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static int calculateGradeByExp(int experience, int oldGrade) {
        if (experience <= 0) {
            return oldGrade;
        }
        
        for (int level = oldGrade + 1; level <= MAX_GRADE; level++) {
            int expNeeded = getExpNeededForLevel(level);
            if (experience < expNeeded) {
                return level - 1;
            }
        }
        
        return MAX_GRADE;
    }
}
