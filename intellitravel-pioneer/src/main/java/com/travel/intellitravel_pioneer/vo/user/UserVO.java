package com.travel.intellitravel_pioneer.vo.user;

import com.travel.intellitravel_pioneer.po.UserPO;
import lombok.Data;

@Data
public class UserVO {

    private Integer id;

    private String username;

    private String name;

    private String grade;

    private Integer experience;

    private String avatar;

    private String telephone;

    private String email;

    private String location;

    private String password;

    private Integer nextGradeExp;

    private Integer expToNextGrade;

    private Double progress;

    private Boolean maxLevelReached;

    private LevelUpInfo levelUpInfo;

    @Data
    public static class LevelUpInfo {
        private Integer oldGrade;
        private Integer newGrade;
        private Integer levelsGained;
        private Boolean isMaxLevel;

        public static LevelUpInfo fromResult(UserPO.LevelUpResult result) {
            if (result == null || !result.isLevelUp()) {
                return null;
            }
            LevelUpInfo info = new LevelUpInfo();
            info.setOldGrade(result.getOldGrade());
            info.setNewGrade(result.getNewGrade());
            info.setLevelsGained(result.getLevelsGained());
            info.setIsMaxLevel(result.getNewGrade() >= UserPO.MAX_GRADE);
            return info;
        }
    }

    public UserPO toPO() {
        UserPO userPO = new UserPO();
        userPO.setId(this.id);
        userPO.setUsername(this.username);
        userPO.setName(this.name);
        userPO.setGrade(this.grade);
        userPO.setExperience(this.experience == null ? 0 : this.experience);
        userPO.setAvatar(this.avatar);
        userPO.setTelephone(this.telephone);
        userPO.setEmail(this.email);
        userPO.setLocation(this.location);
        return userPO;
    }

}
