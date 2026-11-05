package juby.invest.domain.community.converter;

import juby.invest.domain.member.entity.Member;

// 게시물과 댓글이 작성자를 같은 규칙으로 표시하도록 한 곳에 둔다.
public class WriterConverter {

    // 탈퇴하면 닉네임이 비워지므로 작성자 자리를 대신 채운다.
    private static final String WITHDRAWN_WRITER = "탈퇴한 회원";

    public static String nickname(Member member) {
        return member.isDeleted() || member.getNickname() == null ? WITHDRAWN_WRITER : member.getNickname();
    }
}
