/* jconfigint.h.  Generated from jconfigint.h.in by configure.  */
/* libjpeg-turbo build number */
#define BUILD ""

/* How to obtain function inlining. */
#ifndef INLINE
  #ifndef TURBO_FOR_WINDOWS
    #define INLINE inline __attribute__((always_inline))
  #else
    #if defined(__GNUC__)
      #define INLINE inline __attribute__((always_inline))
    #elif defined(_MSC_VER)
      #define INLINE __forceinline
    #else
      #define INLINE
    #endif
  #endif
#endif

/* Define to the full name of this package. */
#define PACKAGE_NAME "libjpeg-turbo"

/* Version number of package */
#define VERSION "1.4.2"
