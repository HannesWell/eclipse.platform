#define UF_IMMUTABLE 0x00000002
#define SF_IMMUTABLE 0x00020000

typedef unsigned int dev_t;
typedef unsigned short mode_t;
typedef unsigned short nlink_t;
typedef unsigned long long ino64_t;
typedef unsigned int uid_t;
typedef unsigned int gid_t;
typedef long long off_t;
typedef long long blkcnt_t;
typedef int blksize_t;

struct timespec {
	long tv_sec;
	long tv_nsec;
};

struct stat {
	dev_t st_dev;
	mode_t st_mode;
	nlink_t st_nlink;
	ino64_t st_ino;
	uid_t st_uid;
	gid_t st_gid;
	dev_t st_rdev;
	int pad0;
	struct timespec st_atimespec;
	struct timespec st_mtimespec;
	struct timespec st_ctimespec;
	struct timespec st_birthtimespec;
	off_t st_size;
	blkcnt_t st_blocks;
	blksize_t st_blksize;
	unsigned int st_flags;
	unsigned int st_gen;
	int st_lspare;
	long long st_qspare[2];
};

int lstat(const char* path, struct stat* buf);
int chflags(const char* path, unsigned int flags);
