package com.barracudaghost;

public class TrialData
{
    public enum TrialType
    {
        UNKNOWN("??", null),
        TEMPOR_TANTRUM("TT", "Tempor Tantrum"),
        JUBBLY_JIVE("JJ", "Jubbly Jive"),
        GWENITH_GLIDE("GG", "Gwenith Glide");

        public final String abbreviation;
        private final String displayName;

        TrialType(String abbreviation, String displayName)
        {
            this.abbreviation = abbreviation;
            this.displayName = displayName;
        }

        public static TrialType fromChatMessage(String message)
        {
            for (TrialType type : values())
            {
                if (type.displayName != null && message.contains(type.displayName))
                {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    public enum Rank
    {
        UNRANKED,
        SWORDFISH,
        SHARK,
        MARLIN
    }

    public final TrialType trialType;
    public final Rank rank;
    public final int finalTimeSeconds;
    public final String username;

    public TrialData(TrialType trialType, Rank rank, int finalTimeSeconds, String username)
    {
        this.trialType = trialType;
        this.rank = rank;
        this.finalTimeSeconds = finalTimeSeconds;
        this.username = username;
    }
}