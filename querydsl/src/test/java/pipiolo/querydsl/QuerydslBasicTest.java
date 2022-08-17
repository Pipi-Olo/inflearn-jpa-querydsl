package pipiolo.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pipiolo.querydsl.dto.MemberDto;
import pipiolo.querydsl.dto.QMemberDto;
import pipiolo.querydsl.dto.UserDto;
import pipiolo.querydsl.entity.Member;
import pipiolo.querydsl.entity.QMember;
import pipiolo.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pipiolo.querydsl.entity.QMember.member;
import static pipiolo.querydsl.entity.QTeam.team;

@Transactional
@SpringBootTest
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory query;

    @BeforeEach
    void beforeEach() {
        query = new JPAQueryFactory(em); // 동시성 문제 없다.

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    void startJPQL() {
        Member findMember = em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void startQuerydsl() {
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        QMember member = new QMember("m"); // 셀프 조인할 때 사용한다.

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void querydsl() {
        Member findMember = query
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void search() {
        Member findMember = query
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void searchAndParam() {
        Member findMember = query
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.eq(10) // and 와 동일하게 동작한다. 추가적으로 중간에 null 값이 들어오면 무시한다. -> 동적 쿼리를 쉽게 짤 수 있다.
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void resultFetch() {
        List<Member> fetch = query
                .selectFrom(member)
                .fetch();

        Member fetchOne = query
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        Member fetchFirst = query
                .selectFrom(QMember.member)
                .fetchFirst();// == limit(1).fetchOne();

        QueryResults<Member> results = query
                .selectFrom(member)
                .fetchResults(); // fetch() + count(), Deprecated -> fetch() 와 count() 쿼리를 별도로 작성
                                 // 애초에 성능이 중요한 환경에서는 fetchResults() 를 쓰면 안 된다. 별도의 count() 쿼리가 필요하다.

        long count = query
                .selectFrom(member)
                .fetchCount(); // Deprecated -> select(member.count()) or select(Wildcard.count) 로 변경

        Long totalCount = query
                //.select(Wildcard.count) //select count(*)
                .select(member.count()) //select count(member.id)
                .from(member)
                .fetchOne();
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순 desc
     * 2. 회원 이름 올림차순 asc
     *    2-1. 회원 이름이 없으면, 마지막에 출력한다. null -> last
     */
    @Test
    void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = query
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        assertThat(result.get(0).getUsername()).isEqualTo("member5");
        assertThat(result.get(1).getUsername()).isEqualTo("member6");
        assertThat(result.get(2).getUsername()).isNull();
    }

    @Test
    void paging1() {
        List<Member> result = query
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void paging2() {
        QueryResults<Member> results = query
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(results.getTotal()).isEqualTo(4); // count 쿼리로 인해 얻은 값 -> 전체 개수
        assertThat(results.getLimit()).isEqualTo(2); // limit 값
        assertThat(results.getOffset()).isEqualTo(1); // offset 값
        assertThat(results.getResults().size()).isEqualTo(2); // contents
    }

    @Test
    void paging3() {
        List<Member> result = query
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        Long totalCount = query
                .select(member.count())
                .from(member)
                .fetchOne();

        assertThat(totalCount).isEqualTo(4);
        assertThat(result.size()).isEqualTo(2); // contents
    }

    @Test
    void aggregation() {
        List<Tuple> result = query
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구하라.
     */
    @Test
    void group() {
        List<Tuple> result = query
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1); // team.name 으로 groupBy 한 결과 팀이 2개 나올 것임

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /**
     * 팀A 에 소속된 모든 회원을 찾아라
     */
    @Test
    void join() {
        List<Member> result = query
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인
     * 연관관계가 없는 엔티티끼리 조인
     * 회원의 이름이 팀 이름과 같은 회원을 조회
     *
     * 모든 회원과 모든 팀을 가져온 다음 조인을 진행한다. AUB
     * 다만, 각 데이터베이스마다 최적화를 진행한다.
     *
     * 카르테시아 조인이 발생한다.
     *
     * 단점 : 과거에는 아예 외부 outer 조인이 불가능하다. 하지만, on 절을 사용해 외부 조인이 가능하도록 업그레이드 되었다.
     */
    @Test
    void thetaJoin() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = query
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 회원과 팀을 조인하면서, 팀 이름이 teamA 인 팀만 조인, 회원은 모두 조회
     * JPQL : select m, t from Member m left join m.team t on t.name = :teamName
     *
     * 결과 : 모든 회원이 조회되고, teamB 를 가진 회원은 member.team 이 null 이다.
     *
     * 만약 inner join 을 사용했다면, member.team 이 null 인 회원들은 아예 조회되지 않는다.
     * 즉, 내부 조인을 사용한 On 절은 사실상 Where 과 결과가 똑같다.
     * 그러므로 내부 조인을 사용한다면 Where 절을 사용하는 것이 낫다. 이유는 where 절이 익숙하고 직관적이기 떄문이다.
     *
     * leftJoin 인 경우 On 절을 사용해야만 한다. Where 절로 해결이 불가능하다.
     */
    @Test
    void joinOnFiltering() {
        List<Tuple> result = query
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 회원을 조회
     */
    @Test
    void joinOnNoRelation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = query
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name)) // 연관관계가 없는 엔티티 -> 오직 이름을 통해서만 조인한다.
                // .leftJoin(member.team, team).on(member.username.eq(team.name)) // 연관관계가 있는 엔티티일 경우!! -> 기본이 id 값으로 조인을 한다. -> id 와 이름으로 조인한다.
                .fetch();
        // SQL 변환할 때,
        // 연관관계가 있다면, id, name 이 같은 것으로 비교하지만, member.team_id = team.id 외래키와 기본키 조인
        // 연관관계가 없다면, 오직 name 만이 같은 것으로 비교한다.

        // leftJoin 이기 때문에 모든 회원을 가져온다. 그 중에 회원 이름 = 팀 이름 인 경우만 팀을 가져온다. 나머지는 null 값을 가진다.
        // 내부 조인이라면 회원 이름 = 팀 이름 인 회원만 가져온다. member.team 을 null 값을 가지는 회원은 아예 안 가져온다.

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    /**
     * member.team LAZY 옵션이기 때문에 아직 프록시이다. 초기화가 진행 안 된 상태
     */
    @Test
    void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = query
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam())).as("페치 조인 미적용").isFalse();
    }

    /**
     * 페치 조인을 사용하면 해당 연관관계에 있는 엔티티를 모두 가져온다.
     * 일반 조인을 사용하면 select 절에있는 것만 가져온다.
     */
    @Test
    void fetchJoin() {
        em.flush();
        em.clear();

        Member findMember = query
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam())).as("페치 조인 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원을 조회
     */
    @Test
    void subQuery() {
        QMember memberSub = new QMember("memberSub"); // member 를 사용하면 엘리언스가 겹쳐서 충돌이 일어난다. 따라서 이 떄 별도로 선언해줘야한다.

        List<Member> result = query
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result)
                .extracting("age").containsExactly(40);
    }

    /**
     * 나이가 평균보다 많은 회원
     */
    @Test
    void subQueryGoe() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = query
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result)
                .extracting("age").containsExactly(30, 40);
    }

    /**
     * 나이가 10 살보다 많은 것에 인 쿼리가 포함된 회원
     * 인 쿼리를 위해 억지로 작성함
     */
    @Test
    void subQueryIn() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = query
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result)
                .extracting("age").containsExactly(20, 30, 40);
    }

    /**
     * 하지만 서브 쿼리의 가장 큰 문제점!!!
     * JPA 는 from 절의 서브쿼리를 지원하지 않는다. -> 당연히 Querydsl 도 불가능하다.
     * JPA 는 select 절 서브쿼리도 지원하지 않는다. -> 하지만 Hibernate 가 select 절 서브쿼리를 지원한다.
     *
     * from 절 서브쿼리 해결방안
     * 1. 서브쿼리를 조인으로 변경한다. (가능할 수도, 불가능할 수도 있다.)
     * 2. 애플리케이션에서 쿼리를 2번 분리해서 실행한다. -> 다만 성능을 잡아먹는다.
     * 3. nativeSQL 을 사용한다.
     *
     * * from 절 서브쿼리 사용 이유
     * 무조건은 아니지만, 특정 화면에 맞추기 위한 데이터를 위해 from 절 서브쿼리를 사용하는 경우가 종종 있다.
     * 이 방법은 좋지 않다.
     * 특정 화면에 맞추기 위해 데이터를 가공하거나 이쁘게 꾸미는 행위는 애플리케이션 혹은 뷰 단에서 해야할 문제이다. 디비에서 해결하는 문제가 아니다.
     * 기본적으로 재사용성이 너무~ 떨어진다.
     *
     * * 한방 쿼리에 너무 집착할 필요 없다. -> 어느정도 느려도 괜찮다면 쿼리를 분리해서 보내라.
     *
     * SQL AntiPatterns 책
     */
    @Test
    void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = query
                .select(member.username,
                        JPAExpressions // static import 를 통해 깔끔하게 정리 가능하다.
                                .select(memberSub.age.avg())
                                .from(memberSub)
                )
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    void basicCase() {
        List<String> result = query
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * Q. 이걸 정말 써야 돼??
     * A. 조회하는 데이터 수를 줄이는 where, filtering 은 좋다. 애초에 데이터 수를 줄이는 행위.
     * 실제 전환하고 바꾸고 보여주는 행위는 디비에서 하지 않는 것이 좋다. 하지만 데이터를 가공하는 행위는 디비단에서 하지 않는 것이 좋다.
     * 물론 하다보면 언젠가는 이런 경우가 효율적이여서 쓰는 경우가 생길것이다. 정말 필수적인 경우가 아니면 다른 방법을 사용하자.
     */
    @Test
    void complexCase() {
        List<String> result = query
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0 ~ 20살")
                        .when(member.age.between(21, 40)).then("21 ~ 20살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void constant() {
        List<Tuple> result = query
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * {username}_{age}
     */
    @Test
    void concat() {
        // member.age 는 String 타입이 아니기 때문에 그냥 적용은 안 된다.
        // stringValue() 쓸일이 많다. 특히 Enum 타입을 쓸 때!
        List<String> result = query
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    // 프로젝션 대상이 1개이면 String, Member, Integer 등 으로 받을 수 있다.
    // 하지만, <String, Integer> 등 여러 객체 형태가 섞여 있으면 한 개의 객채로 받을 수가 없다.
    // 이 때 Tuple 혹은 DTO 형태로 받는다.
    @Test
    void simpleProjection() {
        List<String> result = query
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    // Tuple 은 Querydsl 패키지에 있는 기술이다. -> 리포지토리에서 Tuple 을 사용하는 것은 괜찮지만,
    // 서비스, 컨트롤러 등 다른 계층에 넘어가 특정 기술에 종속하도록 하는 것은 좋지 않다.
    // 즉 리포지토리에서 반환할 때는 Tuple 이 아닌 다른 형태(DTO)로 변환시켜야 한다.
    @Test
    void tupleProjection() {
        List<Tuple> result = query
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);

            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }


    // JPQL 로 DTO 조회하는 방법 -> 패키지명 작성해야함. 별로임.
    @Test
    void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery(
                "select new pipiolo.querydsl.dto.MemberDto(m.username, m.age) from Member m"
                        , MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    // setter 접근법을 통해 MemberDto 생성하는 방법
    // 먼저 MemberDto 객체를 생성하고 setter 메서도를 통해 값을 저장한다.
    // 기본 생성자가 없으면 객체 생성이 애초에 불가능하다. -> 기본 생성자 필요함.
    @Test
    void findDtoByQuerydslSetter() {
        List<MemberDto> result = query
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    // 필드 주입법
    // setter 메소드가 없어도 동작한다.
    // 라이브러리를 통해 private 이여도 필드 주입이 가능하다. 원리는 모르겠다..
    @Test
    void findDtoByQuerydslField() {
        List<MemberDto> result = query
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    // 생성자 주입 방법
    // 이 방법은 기본 생성자가 없어도 된다.
    @Test
    void findDtoByQuerydslConstructor() {
        List<MemberDto> result = query
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void findUserDtoByQuerydsl() {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = query
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"), // 변수 명이 다를 경우, as 를 사용한다. 아니면 null 값이 들어간다.
                        ExpressionUtils.as( // 모든 age 에 max 값을 넣기 위해 서브쿼리 사용. 위에 있는 as 와 같은 방법임. 서븤쿼리를 as 로 "age" 명칭으로 변강함. 왜? max 가 변수명과 다르므로
                                JPAExpressions
                                        .select(memberSub.age.max())
                                        .from(memberSub)
                                , "age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    // 생성자 조회 방식은 변수명이 달라도 문제 없다.
    // 객체 타입만 맞으면 된다.
    @Test
    void findUserDtoByQuerydslConstructor() {
        List<UserDto> result = query
                .select(Projections.constructor(UserDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    // @QueryProjection 의 장점 : 컴파일 시점에 오류 발견이 가능하다. + 커맨드 P 누르면 생성자가 직접 눈에 보인다.
    // Projections.constructor 방식은 생성자 개수 혹은 타입이 달라져도 컴파일 시점에 잡을 수 없다.
    // 런타임 시점이 되어서야 잡을 수 있다.
    // 단점 : Q 파일을 생성해야 한다. + DTO 가 Querydsl이라는 특정 에 대한 의존성이 생긴다.
    // 특히 DTO 는 서비스, 컨트롤러 등 여러 레이어에서 돌아다니는 녀석이 특정 기술에 의존적이다.
    // DTO 를 깔금하게 가져가려면, 리포티토리 DTO 조회 부분이 더러워지고, 대신에 전체 기술 의존도 관점에서 아키텍쳐 깔끔하다.
    // @QueryProjection 을 통해 리포지토리 DTO 조회를 깔끔하게 가져가고, 어차피 Querydsl 기술을 다른 곳에서도 많이 사용하고,
    // 하부 기술이 바뀌걸 갓 같지 않으면 사용한다.
    // 즉 장단점이 존재한다.
    @Test
    void findDtoByQueryProjection() {
        List<MemberDto> result = query
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void dynamicQuery_booleanBuilder() {
        String username = "member1";
        Integer age = 10;

        List<Member> result = searchMemberV1(username, age);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMemberV1(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder(); // 초기값 설정은 생성자를 통해 가능. 다만 무조건 null 이 아니어야 한다.
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return query
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    // 이 방법을 모르고 BooleanBuilder 를 쓰는 사람이 많다.
    // 이 방법을 사용하면 훨씬 깔끔하게 가져갈 수 있다.
    @Test
    void dynamicQuery_WhereParam() {
        String username = "member1";
        Integer age = 10;

        List<Member> result = searchMemberV2(username, age);
        assertThat(result.size()).isEqualTo(1);
    }

    // Where 절에 null 이 들어가면 무시된다. -> 동적 쿼리 가능 이유
    // 장점 :
    // 1. usernameEq 메소드 이름만 봐도 바로 이해가능 구지. 코드를 뜯어서 이해할 필요 앖음.
    // 2. 메소드로 따로 뺏기 때문에 다른 곳에서도 사용 가능.
    // 3. 다른 조합 형태로 조합해서 사용 가능 ex) allEq()
    // 그에 반해 BoolenaBuilder 는 위에서부터 하나하나 이해하면서 내려가야함.
    private List<Member> searchMemberV2(String usernameCond, Integer ageCond) {
        return query
                .selectFrom(member)
                // .where(usernameEq(usernameCond), ageEq(ageCond))
                .where(allEq(usernameCond, ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        if (usernameCond == null) {
            return null;
        }
        return member.username.eq(usernameCond);
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    // 조합해서 쓸려면 BooleanExpression 으로 반환해야함
    // allEq 함수 말고 usernameEq, ageEq 함수들이
    // Predicate, BooleanExpression 타입 모두 where 절에 들어갈 수 있음
    private Predicate allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    // 벌크 연산
    // 주의할 점 : 영속성 컨텍스트를 무시하고 디비에 쿼리가 나간다.
    // 영속성 컨텍스트 != 디비
    // 플러시 및 초기화를 통해 같게 만들어주어야 한다.
    @Test
    void bulkUpdate() {

        // member1 = 10 -> 비회원
        // member2 = 20 -> 비회원
        // member3 = 30 -> 유지
        // member4 = 40 -> 유지

        long count = query
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();

        assertThat(count).isEqualTo(2);
    }

    // 더하기 연산이 이루어진다.
    @Test
    void bulkUpdateAdd() {
        long count = query
                .update(member)
                .set(member.age, member.age.add(1))
                // .set(member.age, member.age.add(-1)) 빼기가 따로 없다. -1일 더하자.
                // .set(member.age, member.age.multiply(2)) 곱하기
                .execute();
    }

    @Test
    void bulkDelete() {
        long count = query
                .delete(member)
                .where(member.age.lt(19))
                .execute();
    }

    // 만약 사용자 정의 함수를 원한다면
    // H2Dialect 를 상속해서 구현한 다음,
    // application.yml 섷정 값을 통해 방언 구현체를 넣어줘야함
    @Test
    void sqlFunction() {
        List<String> result = query
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})", // JPA Dialect 방언에 replace 라는 DB 함수가 등록되어있어야함.
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void sqlFunction2() {
        List<String> result = query
                .select(member.username)
                .from(member)
                // .where(member.username.eq(Expressions.stringTemplate("function('lower', {0})", member.username))) // 소문자 변환 함수
                .where(member.username.eq(member.username.lower())) // 안시 표준에서 제공하는 함수들은 기본적으로 querydls 이 제공해주고 있음.
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}
