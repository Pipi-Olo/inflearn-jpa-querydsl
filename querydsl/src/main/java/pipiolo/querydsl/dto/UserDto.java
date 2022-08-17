package pipiolo.querydsl.dto;

import lombok.Data;

@Data
public class UserDto {

    private String name; // MemberDto 와 별칭이 다르다. 주목! 필드 주입과 setter 방식에서는 null 값이 들어올 수 있다. "as" 를 사용 안 한다면,
                         // 하지만, 생성자 주입 방식에는 String, Integer 등 형태만 맞는다면 변수명은 달라도 문제없다.
    private int age;

    public UserDto() {
    }

    public UserDto(String name, int age) {
        this.name = name;
        this.age = age;
    }
}
