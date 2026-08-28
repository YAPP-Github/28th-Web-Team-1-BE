package com.jobdori.core.domain.prompt

enum class PromptType {
    JD_MULTI_POSTING_SPLIT,        // jd 등록 시 다중 공고 분할
    JD_META_EXTRACTION,            // JD 메타(기업명, 포지션, 소개, 업무, 필요/우대경험, 전형절차) 추출
    JD_KEY_POINTS,                 // jd 공고 핵심(인재상/요구) 요약(서술형) — 서비스에선 JD_META_EXTRACTION에 통합(#73), DB 프롬프트 테스트용으로 유지
    JD_APPLICATION_STRATEGY,       // jd 지원 전략 생성(서술형) — JD 등록 시 워크스페이스 프로필과 함께 생성해 jd.strategy에 저장
    EXPERIENCE_RECOMMENDATION,     // jd-경험 매칭률(전량) + 상위 이유(구조화)
    EXPERIENCE_STAR_EXTRACTION,    // 경험 STAR 추출
    DOCUMENT_TEXT_EXTRACTION,      // 이미지 기반 문서 원문 전사
    RESUME_EXPERIENCE_REWRITE,     // 이력서 문장 생성
    EXPERIENCE_CONTENTS_POLISH,    // Free Style 경험 내용을 STAR로 변환
    PROFILE_CORE_COMPETENCY_GENERATION, // 프로필(이력서 기본 정보) 기반 핵심역량 생성
    PROFILE_TEXT_POLISH,           // 프로필 텍스트(핵심역량/경력 세부/경험명/STAR) 다듬기
    EXPERIENCE_PROJECT_DUPLICATE_MATCH, // 임포트 프로젝트 중복 판정 및 필드 병합
    EXPERIENCE_DUPLICATE_MERGE,    // 중복 프로젝트 내 경험 중복 판정 및 필드 병합
}
