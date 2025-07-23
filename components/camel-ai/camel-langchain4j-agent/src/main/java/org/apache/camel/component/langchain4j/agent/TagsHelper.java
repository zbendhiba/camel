package org.apache.camel.component.langchain4j.agent;

final class TagsHelper {
    private TagsHelper() {
    }

    /**
     * Split the list of tags
     *
     * @param  tagList
     * @return
     */
    public static String[] splitTags(String tagList) {
        return tagList.split(",");
    }
}
