package pipiolo.querydsl.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pipiolo.querydsl.dto.MemberSearchCond;
import pipiolo.querydsl.dto.MemberTeamDto;
import pipiolo.querydsl.dto.QMemberTeamDto;
import pipiolo.querydsl.entity.Member;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static org.springframework.util.StringUtils.hasText;
import static pipiolo.querydsl.entity.QMember.member;
import static pipiolo.querydsl.entity.QTeam.team;

@Transactional
@Repository
public class MemberJpaRepository {

    private final EntityManager em;
    private final JPAQueryFactory query;

    public MemberJpaRepository(EntityManager em) {
        this.em = em;
        this.query = new JPAQueryFactory(em); // JPAQueryFactory 를 스프링 빈으로 등록해서 생성자 주입으로 받아도 된다.

        // 동시성 문제 없다.
        // JPAQueryFactory 가 동시성 문제는 entityManager 에 의존한다.
        // 트랸잭션 단위로 엔티티 매니저가 분리되서 동작하기 때문에 괜찮다.
        // 엔티티 매니저가 프록시가 들어오는데 트랜잭션 단위로 바윈딩되기 때문에 괜찮다.
    }

    public void save(Member member) {
        em.persist(member);
    }

    public Optional<Member> findById(Long id) {
        Member findMember = em.find(Member.class, id);
        return Optional.ofNullable(findMember);
    }

    public List<Member> findAll() {
        return em.createQuery("select m from Member m", Member.class)
                .getResultList();
    }

    // 일반적으로 findAll() 메소드에 직접 입력한다.
    // 문자열이 아닌 자바 함수로 동작하기 때문에 컴파일 시점에 오류 파악 가능!!
    // JPQL 문자열에서 잡아주는 오류는 IDE 에서만 잡아주는 것으로 컴파일이 가능하다.
    // 하지만 querydsl 자바 메소드는 컴파일 자체가 불가능
    public List<Member> findAll_Querydsl() {
        return query
                .selectFrom(member)
                .fetch();
    }

    public List<Member> findByUsername(String username) {
        return em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", username)
                .getResultList();
    }

    public List<Member> findByUsername_Querydsl(String username) {
        return query
                .selectFrom(member)
                .where(member.username.eq(username))
                .fetch();
    }

    public List<MemberTeamDto> searchByBuilder(MemberSearchCond searchCond) {
        BooleanBuilder builder = new BooleanBuilder();
        if (hasText(searchCond.getUsername())) {
            builder.and(member.username.eq(searchCond.getUsername()));
        }

        if (hasText(searchCond.getTeamName())) {
            builder.and(team.name.eq(searchCond.getTeamName()));
        }

        if (searchCond.getAgeGoe() != null) {
            builder.and(member.age.goe(searchCond.getAgeGoe()));
        }

        if (searchCond.getAgeLoe() != null) {
            builder.and(member.age.loe(searchCond.getAgeLoe()));
        }

        return query
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(builder)
                .fetch();
    }

    // BooleanBuilder 보다 BooleanExpression 이 낫다.
    // 1. 재사용성, 2. 조합 이 가능하다.
    // 무엇보다 MemberTeamDto -> 다른 엔티티 혹은 dto 로 변경해도 코드 재사용이 높다.
    public List<MemberTeamDto> search(MemberSearchCond searchCond) {
        return query
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(searchCond.getUsername()),
                        teamNameEq(searchCond.getTeamName()),
                        ageGoe(searchCond.getAgeGoe()),
                        ageLoe(searchCond.getAgeLoe()))
                .fetch();
    }

    // BooleanExpression 은 조합이 가능하기 때문에 Predicate 보다 BooleanExpression 이 낫다.
    private BooleanExpression usernameEq(String username) {
        return hasText(username) ? member.username.eq(username) : null;
    }

    private BooleanExpression teamNameEq(String teamName) {
        return hasText(teamName) ? team.name.eq(teamName) : null;
    }

    private BooleanExpression ageGoe(Integer ageGoe) {
        return ageGoe != null ? member.age.goe(ageGoe) : null;
    }

    private BooleanExpression ageLoe(Integer ageLoe) {
        return ageLoe != null ? member.age.loe(ageLoe) : null;
    }

    public List<Member> searchRe(MemberSearchCond searchCond) {
        return query
                .selectFrom(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(searchCond.getUsername()),
                        teamNameEq(searchCond.getTeamName()),
                        ageGoe(searchCond.getAgeGoe()),
                        ageLoe(searchCond.getAgeLoe()))
                .fetch();
    }

    // 기존 메소드로 조합이 가능하다.
    // 뿐만 아니라 모든 메서드에서 isValid() 등 을 통해 빈 스트링, 널 값들을 한번에 처리할 수 잇다
    private BooleanExpression ageBetween(int ageLoe, int ageGoe) {
        return ageLoe(ageLoe).and(ageGoe(ageGoe));
    }
}
