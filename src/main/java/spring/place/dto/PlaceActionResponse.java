package spring.place.dto;

/** 시트에 띄울 액션 버튼 하나. 서버가 "가능한 것"만 내려보낸다. */
public record PlaceActionResponse(String type, String label) {}
