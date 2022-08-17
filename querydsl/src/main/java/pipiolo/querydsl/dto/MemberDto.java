package pipiolo.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;

@Data
public class MemberDto {

    private String username;
    private int age;

    public MemberDto() {
    }

    @QueryProjection // DTO 를 Q파일로 만들어준다. QMemberDto
    public MemberDto(String username, int age) {
        this.username = username;
        this.age = age;
    }
}
