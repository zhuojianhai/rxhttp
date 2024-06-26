package rxhttp.wrapper.param;

import rxhttp.wrapper.cache.CacheMode;
import rxhttp.wrapper.cache.CacheStrategy;

/**
 * User: ljx
 * Date: 2019-12-15
 * Time: 14:08
 */
public interface ICache<P extends Param<P>> {

    P setCacheKey(String cacheKey);

    P setCacheValidTime(long cacheTime);

    P setCacheMode(CacheMode cacheMode);

    CacheStrategy getCacheStrategy();

    String getCacheKey();

    long getCacheValidTime();

    CacheMode getCacheMode();

}
