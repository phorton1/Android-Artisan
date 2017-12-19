package prh.server.utils;

import prh.utils.Utils;

public class updateCounter
{
    int update_count = 1;

    public int get_update_count()
    {
        return update_count;
    }
    public int inc_update_count()
    {
        update_count = Utils.inc_wrap_int(update_count);
        return update_count;
    }
}
