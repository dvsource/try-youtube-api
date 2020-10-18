package model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class YTChannel
{
    private String id;
    private String title;
    private String description;
    private Long videosCount;
}
