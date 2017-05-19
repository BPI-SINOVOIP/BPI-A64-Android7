#include <linux/module.h>

#define GSL_VERSION	0x20131106
#ifndef NULL
#define	NULL  ((void*)0)
#endif

#define	POINT_MAX		10
#define	PP_DEEP			10
#define	PS_DEEP			10
#define PR_DEEP			10
#define POINT_DEEP		(PP_DEEP + PS_DEEP + PR_DEEP)
#define	PRESSURE_DEEP	8
#define	CONFIG_LENGTH	512
#define TRUE			1
#define FALSE			0
#define FLAG_ABLE		(0x4<<12)
#define FLAG_FILL		(0x2<<12)
#define	FLAG_KEY		(0x1<<12)
#define	FLAG_COOR		(0x0fff0fff)
#define	FLAG_ID			(0xf0000000)

struct gsl_touch_info
{
	int x[10];
	int y[10];
	int id[10];
	int finger_num;	
};

typedef struct
{
	unsigned int i;
	unsigned int j;
	unsigned int min;//distance min
	unsigned int d[POINT_MAX][POINT_MAX];//distance;
}gsl_DISTANCE_TYPE;

typedef union
{
	struct
	{
		unsigned y:12;
		unsigned key:1;
		unsigned fill:1;
		unsigned able:1;
		unsigned predict:1;
		unsigned x:12;
		unsigned id:4;
	};
	struct
	{
		unsigned y:13;
		unsigned rev_2:3;
		unsigned x:12;
		unsigned rev_1:4;
	}dis;
	unsigned int all;
}gsl_POINT_TYPE;

typedef union
{
	struct
	{
		unsigned delay:8;
		unsigned report:8;
		unsigned rev_1:14;
		unsigned able:1;
		unsigned init:1;
	};
	unsigned int all;
}gsl_DELAY_TYPE;


typedef union
{
	struct
	{
		unsigned over_report_mask:1;
		unsigned opposite_x:1;
		unsigned opposite_y:1;
		unsigned opposite_xy:1;
		unsigned line:1;
		unsigned line_neg:1;
		unsigned line_half:1;
		unsigned rev_1:25;
	};
	unsigned int all;
}gsl_FLAG_TYPE;

typedef union
{
	struct
	{
			unsigned over_report_mask:1;
			unsigned opposite_x:1;
			unsigned opposite_y:1;
			unsigned opposite_xy:1;
			unsigned line:1;
			unsigned line_neg:1;
			unsigned line_half:1;
			unsigned middle_drv:1;
			
			unsigned key_only_one:1;
			unsigned key_line:1;
			unsigned refe_rt:1;
			unsigned refe_var:1;
			unsigned base_median:1;
			unsigned key_rt:1;
			unsigned refe_reset:1;
			unsigned sub_cross:1;
			
			unsigned row_neg:1;
			unsigned sub_line_coe:1;
			unsigned sub_row_coe:1;
			unsigned c2f_able:1;
			unsigned thumb:1;
			unsigned graph_h:1;
			unsigned rev_2:2;
			
			unsigned rev_1:8;
	};
	unsigned int all;
}FLAG_TYPE;

static gsl_POINT_TYPE point_array[POINT_DEEP][POINT_MAX];
static gsl_POINT_TYPE *point_pointer[PP_DEEP];
static gsl_POINT_TYPE *point_stretch[PS_DEEP];
static gsl_POINT_TYPE *point_report[PR_DEEP];
static gsl_POINT_TYPE point_now[POINT_MAX];
static gsl_DELAY_TYPE point_delay[POINT_MAX];
int filter_deep[POINT_MAX];

unsigned int pressure_now[POINT_MAX];
unsigned int pressure_array[PRESSURE_DEEP][POINT_MAX];
unsigned int pressure_report[POINT_MAX];
unsigned int *pressure_pointer[PRESSURE_DEEP];

#define	pp							point_pointer
#define	ps							point_stretch
#define	pr							point_report
#define	point_predict				pp[0]
#define	pa							pressure_pointer

static	int inte_count;
static	int point_n;
static	int point_num;
static	int prev_num;
static	int point_near;
static	int point_reset;
static	unsigned int point_shake;
static	unsigned int reset_mask_send;
static	unsigned int reset_mask_max;
static	unsigned int reset_mask_count;
static	FLAG_TYPE global_flag;
static	unsigned int id_first_coe;
static	unsigned int id_speed_coe;
static	unsigned int id_static_coe;
static	unsigned int average;
static	unsigned int soft_average;
static	unsigned int report_delay;
static	unsigned int report_ahead;
static	unsigned char median_dis[4];
static	unsigned int shake_min;
static	int match_y[2];
static	int match_x[2];
static	int ignore_y[2];
static	int ignore_x[2];
static	int screen_y_max;
static	int screen_x_max;
static	int point_num_max;
static	unsigned int drv_num;
static	unsigned int sen_num;
static	unsigned int drv_num_nokey;
static	unsigned int sen_num_nokey;
static	unsigned int coordinate_correct_able;
static	unsigned int coordinate_correct_coe_x[64];
static	unsigned int coordinate_correct_coe_y[64];
static	unsigned int edge_cut[4];
static	unsigned int stretch_array[4*4*2];
static	unsigned int shake_all_array[2*8];
static	unsigned int reset_mask_dis;
static	unsigned int reset_mask_type;
static	unsigned int key_map_able;
static	unsigned int key_range_array[8*3];
static	int  filter_able;
static	unsigned int filter_coe[4];
static	unsigned int multi_x_array[4],multi_y_array[4];
static	unsigned int multi_group[4][64];
static	int ps_coe[4][8],pr_coe[4][8];
static	int point_repeat[2];
static	int near_set[2];
static	int diagonal;

static	int near_period;
static	int near_count;
static	int near_out;
static	int near_ignore;
static	int near_data_prev;
static	int near_inte;
static	int near_refe_start;
static	int near_data[128];
static	unsigned int point_corner;
static	int point_only;
//-------------------------------------------------
static	unsigned int config_static[CONFIG_LENGTH];
//-------------------------------------------------
static void SortBubble(int t[],int size)
{
	int temp = 0;
	int m,n;
	for(m=0;m<size;m++)
	{
		for(n=m+1;n<size;n++)
		{
			temp = t[m];
			if (temp>t[n])
			{
				t[m] = t[n];
				t[n] = temp;
			}
		}
	}
}

static int Sqrt(int d)
{
	int ret = 0;
	int i;
	for(i=14;i>=0;i--)
	{
		if((ret + (0x1<<i))*(ret + (0x1<<i)) <= d)
			ret |= (0x1<<i);
	}
	return ret;
}

static void PointRepeat(void)
{
	int i,j;
	int x,y;
	int x_min,x_max,y_min,y_max;
	int pn;
	if(point_near)
		point_near --;
	if(prev_num > point_num)
		point_near = 8;
	if(point_repeat[0]==0 || point_repeat[1]==0)
	{
		if(point_near)
			pn = 96;
		else
			pn = 32;
	}
	else
	{
		if(point_near)
			pn = point_repeat[1];
		else
			pn = point_repeat[0];
	}
	for(i=0;i<POINT_MAX;i++)
	{
		if(point_now[i].all == 0)
			continue;
		x_min = point_now[i].x - pn;
		x_max = point_now[i].x + pn;
		y_min = point_now[i].y - pn;
		y_max = point_now[i].y + pn;
		for(j=i+1;j<POINT_MAX;j++)
		{
			if(point_now[j].all == 0)
				continue;
			x = point_now[j].x;
			y = point_now[j].y;
			if(x>x_min && x<x_max && y>y_min && y<y_max)
			{
				point_now[i].x = (point_now[i].x + point_now[j].x + 1) / 2;
				point_now[i].y = (point_now[i].y + point_now[j].y + 1) / 2;
				point_now[j].all = 0;
				i--;
				point_near = 8;
				break;
			}
		}
	}
}

static void PointPointer(void)
{
	int i,pn;
	point_n ++ ;
	if(point_n >= PP_DEEP * PS_DEEP * PR_DEEP * PRESSURE_DEEP)
		point_n = 0;
	pn = point_n % PP_DEEP;
	for(i=0;i<PP_DEEP;i++)
	{
		pp[i] = point_array[pn];
		if(pn == 0)
			pn = PP_DEEP - 1;
		else
			pn--;
	}
	pn = point_n % PS_DEEP;
	for(i=0;i<PS_DEEP;i++)
	{
		ps[i] = point_array[pn+PP_DEEP];
		if(pn == 0)
			pn = PS_DEEP - 1;
		else
			pn--;
	}
	pn = point_n % PR_DEEP;
	for(i=0;i<PR_DEEP;i++)
	{
		pr[i] = point_array[pn+PP_DEEP+PS_DEEP];
		if(pn == 0)
			pn = PR_DEEP - 1;
		else
			pn--;
	}
	pn = point_n % PRESSURE_DEEP;
	for(i=0;i<PRESSURE_DEEP;i++)
	{
		pa[i] = pressure_array[pn];
		if(pn == 0)
			pn = PRESSURE_DEEP - 1;
		else
			pn--;
	}
	//-------------------------------------------------------------------------
	pn = 0;
	for(i=0;i<POINT_MAX;i++)
	{
		point_now[i].all &= (FLAG_COOR | FLAG_KEY|FLAG_ABLE);
		if(point_now[i].all)
			point_now[pn++].all = point_now[i].all;
	}
	point_num = pn;
	for(i=pn;i<POINT_MAX;i++)
		point_now[i].all = 0;
}

static unsigned int CCO(unsigned int x,unsigned int coe[],int k)
{
	if(k == 0)
	{
		if(x & 32)
			return (x & ~31)+(31 - (coe[31-(x&31)] & 31));
		else
			return (x & ~31)+(coe[x&31] & 31);
	}
	if(k == 1)
	{
		if(x & 64)
			return (x & ~63)+(63 - (coe[63-(x&63)] & 63));
		else
			return (x & ~63)+(coe[x&63] & 63);
	}
	if(k == 2)
	{
		return (x & ~63)+(coe[x&63] & 63);
	}
	return 0;
}

static void CoordinateCorrect(void)
{
	typedef struct
	{
		unsigned int range;
		unsigned int group; 
	}MULTI_TYPE;
#ifdef LINE_MULTI_SIZE
	#define	LINE_SIZE	LINE_MULTI_SIZE
#else
	#define	LINE_SIZE		4
#endif
	int i,j;
	unsigned int *px[LINE_SIZE+1],*py[LINE_SIZE+1];
	MULTI_TYPE multi_x[LINE_SIZE],multi_y[LINE_SIZE];
	unsigned int edge_size = 64;
	if((coordinate_correct_able&0xf) == 0)
		return;
	for(i=0;i<LINE_SIZE;i++)
	{
		multi_x[i].range = multi_x_array[i] & 0xffff;
		multi_x[i].group = multi_x_array[i] >> 16;
		multi_y[i].range = multi_y_array[i] & 0xffff;
		multi_y[i].group = multi_y_array[i] >> 16;
	}
	px[0] = coordinate_correct_coe_x;
	py[0] = coordinate_correct_coe_y;
	for(i=0;i<LINE_SIZE;i++)
	{
		px[i+1] = NULL;
		py[i+1] = NULL;
	}
	j=1;
	for(i=0;i<LINE_SIZE;i++)
		if(multi_x[i].range && multi_x[i].group<LINE_SIZE)
			px[j++] = multi_group[multi_x[i].group];
	j=1;
	for(i=0;i<LINE_SIZE;i++)
		if(multi_y[i].range && multi_y[i].group<LINE_SIZE)
			py[j++] = multi_group[multi_y[i].group];
	for(i=0;i<(int)point_num && i<POINT_MAX;i++)
	{
		if(point_now[i].all==0)
			break;
		if(point_now[i].key!=0)
			continue;
		if(point_now[i].x >= edge_size && point_now[i].x <= drv_num_nokey*64 - edge_size)
		{
			for(j=0;j<LINE_SIZE+1;j++)
			{
				if(!(j>=LINE_SIZE || px[j+1] == NULL || multi_x[j].range == 0 || point_now[i].x < multi_x[j].range))
					continue;
				point_now[i].x = CCO(point_now[i].x,px[j],(coordinate_correct_able>>4)&0xf);
				break;
			}
		}
		if(point_now[i].y >= edge_size && point_now[i].y <= sen_num_nokey*64 - edge_size)
		{
			for(j=0;j<LINE_SIZE+1;j++)
			{
				if(!(j>=LINE_SIZE || py[j+1] == NULL || multi_y[j].range == 0 || point_now[i].y < multi_y[j].range))
					continue;
				point_now[i].y = CCO(point_now[i].y,py[j],(coordinate_correct_able>>8)&0xf);
				break;
			}
		}
	}
#undef LINE_SIZE
}

static void PointPredictOne(unsigned int n)
{
	pp[0][n].all = pp[1][n].all & FLAG_COOR;
	pp[0][n].predict = 0;
}

static void PointPredictTwo(unsigned int n)
{
	unsigned int t;
	pp[0][n].all = 0;
	t = pp[1][n].x * 2;
	if(t > pp[2][n].x)
		t -= pp[2][n].x;
	else
		t = 0;
	if(t > 0xfff)
		pp[0][n].x = 0xfff;
	else
	pp[0][n].x = t;
	t = pp[1][n].y * 2;
	if(t > pp[2][n].y)
		t -= pp[2][n].y;
	else
		t = 0;
	if(t > 0xfff)
		pp[0][n].y = 0xfff;
	else
	pp[0][n].y = t;
	pp[0][n].predict = 1;
}

static void PointPredictThree(unsigned int n)
{
	unsigned int t,t2;
	pp[0][n].all = 0;
	t = pp[1][n].x * 5 + pp[3][n].x;
	t2= pp[2][n].x * 4;
	if(t > t2)
		t -= t2;
	else
		t = 0;
	t /= 2;
	if(t > 0xfff)
		pp[0][n].x = 0xfff;
	else
		pp[0][n].x = t;
	t = pp[1][n].y * 5 + pp[3][n].y;
	t2= pp[2][n].y * 4;
	if(t > t2)
		t -= t2;
	else
		t = 0;
	t /= 2;
	if(t > 0xfff)
		pp[0][n].y = 0xfff;
	else
		pp[0][n].y = t;
	pp[0][n].predict = 1;
}

static void PointPredict(void)
{
	int i;
	for(i=0;i<POINT_MAX;i++)
	{
		if(pp[1][i].all != 0)
		{
			if(pp[2][i].all == 0 || pp[2][i].fill != 0 || pp[3][i].fill != 0)
			{
				PointPredictOne(i);
			}
			else if(pp[2][i].all != 0)
			{
				if(pp[3][i].all != 0)
					PointPredictThree(i);
				else
					PointPredictTwo(i);
			}
		}
		else
			pp[0][i].all = 0x0fff0fff;
		if(pp[1][i].key)
			pp[0][i].all |= FLAG_KEY;
	}
}

static unsigned int PointDistance(gsl_POINT_TYPE *p1,gsl_POINT_TYPE *p2)
{
	int a,b,ret;
	a = p1->dis.x;
	b = p2->dis.x;
	ret = (a-b)*(a-b);
	a = p1->dis.y;
	b = p2->dis.y;
	ret += (a-b)*(a-b);
	return ret;
}

static void DistanceInit(gsl_DISTANCE_TYPE *p)
{
	int i;
	unsigned int *p_int = &(p->d[0][0]);
	for(i=0;i<POINT_MAX*POINT_MAX;i++)
		*p_int++ = 0x7fffffff;
}

static int DistanceMin(gsl_DISTANCE_TYPE *p)
{
	int i,j;
	p->min = 0x7fffffff;
	for(j=0;j<POINT_MAX;j++)
	{
		for(i=0;i<POINT_MAX;i++)
		{
			if(p->d[j][i] < p->min)
			{
				p->i = i;
				p->j = j;
				p->min = p->d[j][i];
			}
		}
	}
	if(p->min == 0x7fffffff)
		return 0;
	return 1;
}

static void DistanceIgnore(gsl_DISTANCE_TYPE *p)
{
	int i,j;
	for(i=0;i<POINT_MAX;i++)
		p->d[p->j][i] = 0x7fffffff;
	for(j=0;j<POINT_MAX;j++)
		p->d[j][p->i] = 0x7fffffff;
}

static int SpeedGet(int d)
{
	int i;
	for(i=8;i>0;i--)
	{
		if(d > 0x100<<i)
			break;
	}
	return i;
}

static void PointId(void)
{
	int i,j;
	gsl_DISTANCE_TYPE distance;
	unsigned int id_speed[POINT_MAX];
	DistanceInit(&distance);
	for(i=0;i<POINT_MAX;i++)
	{
		if(pp[0][i].predict == 0 || pp[1][i].fill != 0)
			id_speed[i] = id_first_coe;
		else
			id_speed[i] = SpeedGet( PointDistance(&pp[1][i],&pp[0][i]) );
	}
	for(i=0;i<POINT_MAX;i++)
	{
		if(pp[0][i].all == FLAG_COOR)
			continue;
		for(j=0;j<point_num && j<POINT_MAX;j++)
		{
			distance.d[j][i] = PointDistance(&point_now[j],&pp[0][i]);
		}
	}
	if(point_num == 0)
		return;
	if(point_only)
	{
		do
		{
			if(DistanceMin(&distance) == 0)
				break;
			point_now[distance.j].id = 1;
			point_now[0].all = point_now[distance.j].all;
			DistanceIgnore(&distance);
		}
		while(0);
		point_num = 1;
	}
	else
	{
		for(j=0;j<point_num && j<POINT_MAX;j++)
		{
			if(DistanceMin(&distance) == 0)
				break;
			if(distance.min >= (id_static_coe + id_speed[distance.i] * id_speed_coe) /**average/(soft_average+1)*/)
			{
				point_now[distance.j].id = 0xf;//new id
				continue;
			}
			point_now[distance.j].id = distance.i+1;
			DistanceIgnore(&distance);
		}
	}
}

static int ClearLenPP(int i)
{
	int n;
	for(n=1;n<PP_DEEP;n++)
	{
		if(pp[n][i].all)
			break;
	}
	return n;
}
static void PointNewId(void)
{
	int i,j,id;
	for(j=0;j<point_num && j<POINT_MAX;j++)
	{
		if(point_now[j].id == 0 || point_now[j].id == 0xf)
		{
			for(id=1;id<=POINT_MAX;id++)
			{
				for(i=0;i<POINT_MAX;i++)
					if(point_now[i].id == (unsigned int)id)// || pp[1][i].id == id || pp[2][i].id == id)
						break;
				if(i == POINT_MAX)
				{
					if(ClearLenPP(id-1) > (int)(1+1))
						break;
				}
			}
			if(id > POINT_MAX)
				continue;
			if(point_now[j].able)
				continue;
			
			point_now[j].id = id;
		}
	}
}

static void PointOrder(void)
{
	int i,id;
	for(i=0;i<POINT_MAX;i++)
	{
		if(filter_able<0 || filter_able>1)
		{
			pp[0][i].id = i+1;
			pp[0][i].fill = 1;
		}
		else
		{
			pp[0][i].all =0;
		}
	}
	for(i=0;i<point_num && i<POINT_MAX;i++)
	{
		id = point_now[i].id;
		if(id == 0 || id > POINT_MAX)
			continue;
		pp[0][id-1].all = point_now[i].all & (FLAG_ID | FLAG_COOR | FLAG_KEY | FLAG_ABLE);
		pa[0][id-1] = pressure_now[i];
	}
	for(i=0;i<POINT_MAX;i++)
	{
		if(pp[0][i].fill == 0)
			continue;
		if(pp[1][i].all==0 || pp[1][i].fill!=0)
			pp[0][i].all = 0;
	}
}

static void PointCross(void)
{
	unsigned int i,j;
	unsigned int t;
	for(j=0;j<POINT_MAX;j++)
	{
		for(i=j+1;i<POINT_MAX;i++)
		{
			if(pp[0][i].all == 0 || pp[0][j].all == 0
			|| pp[1][i].all == 0 || pp[1][j].all == 0)
				continue;
			if(((pp[0][j].x < pp[0][i].x && pp[1][j].x > pp[1][i].x)
			 || (pp[0][j].x > pp[0][i].x && pp[1][j].x < pp[1][i].x))
			&& ((pp[0][j].y < pp[0][i].y && pp[1][j].y > pp[1][i].y)
			 || (pp[0][j].y > pp[0][i].y && pp[1][j].y < pp[1][i].y)))
			{
				t = pp[0][i].x;
				pp[0][i].x = pp[0][j].x;
				pp[0][j].x = t;
				t = pp[0][i].y;
				pp[0][i].y = pp[0][j].y;
				pp[0][j].y = t;
			}
		}
	}
}

static void GetPointNum(gsl_POINT_TYPE *pt)
{
	int i;
	point_num = 0;
	for(i=0;i<POINT_MAX;i++)
		if(pt[i].all != 0)
			point_num++;
}

static void PointDelay(void)
{
	int i,j;
	for(i=0;i<POINT_MAX;i++)
	{
		if(report_delay == 0)
		{//�ر�
			point_delay[i].all = 0;
			point_delay[i].able = 1;
			continue;
		}
		if(pp[0][i].all!=0 && point_delay[i].init == 0 && point_delay[i].able == 0)
		{
			if(point_num == 0)
				continue;
			point_delay[i].delay  = (report_delay >> 3*((point_num>10?10:point_num)-1))&0x7;
			point_delay[i].report = (report_ahead >> 3*((point_num>10?10:point_num)-1))&0x7;
			if(point_delay[i].report > point_delay[i].delay)
				point_delay[i].report = point_delay[i].delay;
			point_delay[i].init = 1;
		}
		if(pp[0][i].all == 0)
		{
			point_delay[i].init = 0;
		}
		if(point_delay[i].able == 0 && point_delay[i].init != 0)
		{
			for(j=0;j<=(int)point_delay[i].delay;j++)
				if(pp[j][i].all == 0 || pp[j][i].fill != 0 || pp[j][i].able!=0)
					break;
			if(j <= (int)point_delay[i].delay)
				continue;
			point_delay[i].able = 1;
		}
		if(pp[point_delay[i].report][i].all == 0)
		{
			point_delay[i].able = 0;
			continue;
		}
		if(point_delay[i].able == 0)
			continue;
		if(point_delay[i].report)
		{
			if(PointDistance(&pp[point_delay[i].report][i],&pp[point_delay[i].report-1][i]) < 3*3)
				point_delay[i].report --;
		}
	}
}

static void FilterOne(int i,int *ps_c,int *pr_c,int denominator)
{
	int j;
	int x=0,y=0;
	pr[0][i].all = ps[0][i].all;
	if(pr[0][i].all == 0)
		return;
	if(denominator <= 0)
		return;
	for(j=0;j<8;j++)
	{
		x += (int)pr[j][i].x * (int)pr_c[j] + (int)ps[j][i].x * (int)ps_c[j];
		y += (int)pr[j][i].y * (int)pr_c[j] + (int)ps[j][i].y * (int)ps_c[j];
	}
	x = (x + denominator/2) / denominator;
	y = (y + denominator/2) / denominator;
	if(x < 0)
		x = 0;
	if(x > 0xfff)
		x = 0xfff;
	if(y < 0)
		y = 0;
	if(y > 0xfff)
		y = 0xfff;
	pr[0][i].x = x;
	pr[0][i].y = y;
}

static unsigned int FilterSpeed(int i)
{
	return (Sqrt(PointDistance(&ps[0][i],&ps[1][i])) + Sqrt(PointDistance(&ps[1][i],&ps[2][i])))/2;
}

static void PointFilter(void)
{
	int i,j;
	int speed_now;
	int filter_speed[6];
	int ps_c[8];
	int pr_c[8];
	for(i=0;i<POINT_MAX;i++)
	{
		pr[0][i].all = ps[0][i].all;
	}
	for(i=0;i<POINT_MAX;i++)
	{
		if(pr[0][i].all!=0 && pr[1][i].all == 0)
		{
			for(j=1;j<PR_DEEP;j++)
				pr[j][i].all = ps[0][i].all;
			for(j=1;j<PS_DEEP;j++)
				ps[j][i].all = ps[0][i].all;
		}
	}
	if(filter_able >=0 && filter_able <= 1)
		return;
	if(filter_able > 1)
	{
		for(i=0;i<8;i++)
		{
			ps_c[i] = (filter_coe[i/4] >> ((i%4)*8)) & 0xff;
			pr_c[i] = (filter_coe[i/4+2] >> ((i%4)*8)) & 0xff;
			if(ps_c[i] >= 0x80)
				ps_c[i] |= 0xffffff00;
			if(pr_c[i] >= 0x80)
				pr_c[i] |= 0xffffff00;
		}
		for(i=0;i<POINT_MAX;i++)
		{
			FilterOne(i,ps_c,pr_c,filter_able);
		}
	}
	else if(filter_able < 0)
	{
		for(i=0;i<4;i++)
			filter_speed[i+1] = median_dis[i];
		filter_speed[0] = median_dis[0] * 2 - median_dis[1];
		filter_speed[5] = median_dis[3] /2;
		for(i=0;i<POINT_MAX;i++)
		{
 			if(pr[0][i].all == 0)
			{
				filter_deep[i] = 0;
				continue;
			}
			speed_now = FilterSpeed(i);
			if(filter_deep[i] > 0 && speed_now > filter_speed[filter_deep[i]+1 - 2])
				filter_deep[i] --;
			else if(filter_deep[i] < 3 && speed_now < filter_speed[filter_deep[i]+1 + 2])
				filter_deep[i] ++;
				
			FilterOne(i,ps_coe[filter_deep[i]],pr_coe[filter_deep[i]],0-filter_able);
		}
	}
}
static unsigned int KeyMap(int *drv,int *sen)
{
	typedef struct
	{
		unsigned int up_down,left_right;
		unsigned int coor;
	}KEY_TYPE_RANGE;
	KEY_TYPE_RANGE *key_range = (KEY_TYPE_RANGE * )key_range_array;
	int i;
	for(i=0;i<8;i++)
	{
		if((unsigned int)*drv >= (key_range[i].up_down >> 16) 
		&& (unsigned int)*drv <= (key_range[i].up_down & 0xffff)
		&& (unsigned int)*sen >= (key_range[i].left_right >> 16)
		&& (unsigned int)*sen <= (key_range[i].left_right & 0xffff))
		{
			*sen = key_range[i].coor >> 16;
			*drv = key_range[i].coor & 0xffff;
			return key_range[i].coor;
		}
	}
	return 0;
}

static unsigned int ScreenResolution(gsl_POINT_TYPE *p)
{
	int x,y;
	unsigned int id;
	x = p->x;
	y = p->y;
	id = p->id;
	if(p->key == FALSE)
	{
		y = ((y - match_y[1]) * match_y[0] + 2048)/4096;
		x = ((x - match_x[1]) * match_x[0] + 2048)/4096 ;
	}
	y = y * (int)screen_y_max / ((int)sen_num_nokey * 64);
	x = x * (int)screen_x_max / ((int)drv_num_nokey * 64);
	if(p->key == FALSE)
	{
		if((ignore_y[0]!=0 || ignore_y[1]!=0))
		{
			if(y < ignore_y[0])
				return 0;
			if(ignore_y[1] <= screen_y_max/2 && y > screen_y_max - ignore_y[1])
				return 0;
			if(ignore_y[1] >= screen_y_max/2 && y > ignore_y[1])
				return 0;
		}
		if(ignore_x[0]!=0 || ignore_x[1]!=0)
		{
			if(x < ignore_x[0])
				return 0;
			if(ignore_x[1] <= screen_y_max/2 && x > screen_x_max - ignore_x[1])
				return 0;
			if(ignore_x[1] >= screen_y_max/2 && x > ignore_x[1])
				return 0;
		}
		if(y <= (int)edge_cut[2])
			y = (int)edge_cut[2] + 1;
		if(y >= screen_y_max - (int)edge_cut[3])
			y = screen_y_max - (int)edge_cut[3] - 1;
		if(x <= (int)edge_cut[0])
			x = (int)edge_cut[0] + 1;
		if(x >= screen_x_max - (int)edge_cut[1])
			x = screen_x_max - (int)edge_cut[1] - 1;
		if(global_flag.opposite_x)
			y = screen_y_max - y;
		if(global_flag.opposite_y)
			x = screen_x_max - x;
		if(global_flag.opposite_xy)
		{
			y ^= x;
			x ^= y;
			y ^= x;
		}
	}
	else
	{
		if(y < 0)
			y = 0;
		if(x < 0)
			x = 0;
		if((key_map_able & 0x1) != FALSE && KeyMap(&x,&y) == 0)
			return 0;
	}
	return ((id<<28) + ((y<<16) & 0x0fff0000) + (x & 0x0000ffff));
}

static void PointReport(struct gsl_touch_info *cinfo)
{
	int i;
	int id;
	unsigned int data;
	int num = 0;
	if(point_num > point_num_max && global_flag.over_report_mask != 0)
	{
		point_num = 0;
		cinfo->finger_num = 0;	
		return;
	}
	for(i=0;i<POINT_MAX;i++)
	{
		if(point_delay[i].able == 0)
			continue;
		if(point_delay[i].report >= PR_DEEP)
			continue;
		id = pr[point_delay[i].report][i].id;
		if(id>0 && id<=point_num_max)
		{
			data = ScreenResolution(&pr[point_delay[i].report][i]);
			if(data == 0)
				continue;
			cinfo->x[num] = (data >> 16) & 0xfff;
			cinfo->y[num] = data & 0xfff;
			cinfo->id[num] = data >> 28; 
			pressure_now[num] = pressure_report[i];
			num ++; 
		}
	}
	point_num = num;
	cinfo->finger_num = num;
}



static void PointEdge(void)
{
	typedef struct
	{
		int range;
		int coe;
	}STRETCH_TYPE;
	typedef struct
	{
		STRETCH_TYPE up[4];
		STRETCH_TYPE down[4];
		STRETCH_TYPE left[4];
		STRETCH_TYPE right[4];
	}STRETCH_TYPE_ALL;//stretch;
	STRETCH_TYPE_ALL *stretch;
	int i,id;
	int data[2];
	int x,y;
	int sac[4*4*2];//stretch_array_copy
	if(screen_x_max == 0 || screen_y_max == 0)
		return;
	for(i=0;i<4*4*2;i++)
		sac[i] = stretch_array[i];
	stretch = (STRETCH_TYPE_ALL *)sac;
	for(i=0;i<4;i++)
	{
		if(stretch->right[i].range > screen_y_max * 64 / 128
		|| stretch->down [i].range > screen_x_max * 64 / 128)
		{
			for(i=0;i<4;i++)
			{
				if(stretch->up[i].range)
					stretch->up[i].range = stretch->up[i].range * drv_num_nokey * 64 / screen_x_max;
				if(stretch->down[i].range)
					stretch->down[i].range = (screen_x_max - stretch->down[i].range) * drv_num_nokey * 64 / screen_x_max;
				if(stretch->left[i].range)
					stretch->left[i].range = stretch->left[i].range * sen_num_nokey * 64 / screen_y_max;
				if(stretch->right[i].range)
					stretch->right[i].range = (screen_y_max - stretch->right[i].range) * sen_num_nokey * 64 / screen_y_max;
			}
			break;
		}
	}
	for(id=0;id<POINT_MAX;id++)
	{
		if(point_now[id].all == 0 || point_now[id].key!=0)
			continue;
		x = point_now[id].x;
		y = point_now[id].y;

		data[0] = 0;
		data[1] = y;
		for(i=0;i<4;i++)
		{
			if(stretch->left[i].range == 0)
				break;
			if(data[1] < stretch->left[i].range)
			{
				data[0] += (stretch->left[i].range - data[1]) * stretch->left[i].coe/128;
				data[1] = stretch->left[i].range;
			}
		}
		y = data[1] - data[0];
		if(y <= 0)
			y = 1;
		if(y >= (int)sen_num_nokey*64)
			y = sen_num_nokey*64 - 1;

		data[0] = 0;
		data[1] = sen_num_nokey * 64 - y;
		for(i=0;i<4;i++)
		{
			if(stretch->right[i].range == 0)
				break;
			if(data[1] < stretch->right[i].range)
			{
				data[0] += (stretch->right[i].range - data[1]) * stretch->right[i].coe/128;
				data[1] = stretch->right[i].range;
			}
		}
		y = sen_num_nokey * 64 - (data[1] - data[0]);
		if(y <= 0)
			y = 1;
		if(y >= (int)sen_num_nokey*64)
			y = sen_num_nokey*64 - 1;

		data[0] = 0;
		data[1] = x;
		for(i=0;i<4;i++)
		{
			if(stretch->up[i].range == 0)
				break;
			if(data[1] < stretch->up[i].range)
			{
				data[0] += (stretch->up[i].range - data[1]) * stretch->up[i].coe/128;
				data[1] = stretch->up[i].range;
			}
		}
		x = data[1] - data[0];
		if(x <= 0)
			x = 1;
		if(x >= (int)drv_num_nokey*64)
			x = drv_num_nokey*64 - 1;

		data[0] = 0;
		data[1] = drv_num_nokey * 64 - x;
		for(i=0;i<4;i++)
		{
			if(stretch->down[i].range == 0)
				break;
			if(data[1] < stretch->down[i].range)
			{
				data[0] += (stretch->down[i].range - data[1]) * stretch->down[i].coe/128;
				data[1] = stretch->down[i].range;
			}
		}
		x = drv_num_nokey * 64 - (data[1] - data[0]);
		if(x <= 0)
			x = 1;
		if(x >= (int)drv_num_nokey*64)
			x = drv_num_nokey*64 - 1;

		point_now[id].x = x;
		point_now[id].y = y;
	}
}

static void PointStretch(void)
{
	typedef struct  
	{
		int dis;
		int coe;
	}SHAKE_TYPE;
	SHAKE_TYPE * shake_all = (SHAKE_TYPE *) shake_all_array;
	int i,j;
	int dn;
	int dr;
	int dc[9],ds[9];
	int len = 8;
	for(i=0;i<POINT_MAX;i++)
	{
		ps[0][i].all = pp[0][i].all;
	}	
	for(i=0;i<POINT_MAX;i++)
	{
		if(pp[0][i].all == 0)
		{
			point_shake &= ~(0x1<<i);
			continue;
		}
		if(ps[1][i].all == 0)
		{
			continue;
		}
		else if((point_shake & (0x1<<i)) == 0 && pp[0][i].key == 0)
		{
			if(PointDistance(&pp[0][i],&ps[1][i]) < (unsigned int)shake_min)
			{
				ps[0][i].all = ps[1][i].all;
				continue;
			}
			else
				point_shake |= (0x1<<i);
		}
	}
	for(i=0;i<len;i++)
	{
		if(shake_all[i].dis == 0)
		{
			len=i;
			break;
		}
	}
	if(len == 1)
	{
		ds[0] = Sqrt(shake_all[0].dis);
		for(i=0;i<POINT_MAX;i++)
		{
			if(ps[1][i].all == 0)
			{
				for(j=1;j<PS_DEEP;j++)
					ps[j][i].all = ps[0][i].all;
				continue;
			}
			if((point_shake & (0x1<<i)) == 0)
				continue;
			dn = PointDistance(&pp[0][i],&ps[1][i]);
			dn = Sqrt(dn);
			dr = dn>ds[0] ? dn-ds[0] : 0;
			if(dn == 0 || dr == 0)
			{
				ps[0][i].x = ps[1][i].x;
				ps[0][i].y = ps[1][i].y;
			}
			else
			{
				ps[0][i].x = (int)ps[1][i].x + ((int)pp[0][i].x - (int)ps[1][i].x) * dr / dn;
				ps[0][i].y = (int)ps[1][i].y + ((int)pp[0][i].y - (int)ps[1][i].y) * dr / dn;
			}
		}
		
	}
	else if(len > 2)
	{
		for(i=0;i<8 && i<len;i++)
		{
			ds[i+1] = Sqrt(shake_all[i].dis);
			dc[i+1] = ds[i+1] * shake_all[i].coe;
		}
		if(shake_all[0].coe >= 128 || shake_all[0].coe <= shake_all[1].coe)
		{
			ds[0] = ds[1];
			dc[0] = dc[1];
		}
		else
		{
			ds[0] = ds[1] + (128 - shake_all[0].coe)*(ds[1]-ds[2])/(shake_all[0].coe - shake_all[1].coe);
			dc[0] = ds[0] * 128;
		}
		for(i=0;i<POINT_MAX;i++)
		{
			if(ps[1][i].all == 0)
			{
				for(j=1;j<PS_DEEP;j++)
					ps[j][i].all = ps[0][i].all;
				continue;
			}
			if((point_shake & (0x1<<i)) == 0)
				continue;
			dn = PointDistance(&pp[0][i],&ps[1][i]);
			dn = Sqrt(dn);
			if(dn >= ds[0])
			{
				continue;
			}
			for(j=0;j<=len;j++)
			{
				if(j == len || dn == 0)
				{
					ps[0][i].x = ps[1][i].x;
					ps[0][i].y = ps[1][i].y;
				}
				else if(ds[j] > dn && dn >=ds[j+1])
				{
					dr = dc[j+1] + (dn - ds[j+1]) * (dc[j] - dc[j+1]) / (ds[j] - ds[j+1]);
					ps[0][i].x = (int)ps[1][i].x + ((int)pp[0][i].x - (int)ps[1][i].x) * dr / dn / 128;
					ps[0][i].y = (int)ps[1][i].y + ((int)pp[0][i].y - (int)ps[1][i].y) * dr / dn / 128;
					break;
				}
			}
		}
	}
	else
	{
		return;
	}
}

static void ResetMask(void)
{
	if(reset_mask_dis ==0 || reset_mask_type == 0)
		return;
	if(reset_mask_max == 0xffffffff)
		return;
	if(reset_mask_send)
	{
		reset_mask_send = 0;
		reset_mask_max = 0xffffffff;
		return;
	}
	if(reset_mask_max == 0xfffffff1)
	{
		if(point_num == 0) 
			reset_mask_max = 0xf0000000 + 1;//(reset_mask_dis >> 24)*2;
		return;
	}
	if(reset_mask_max >  0xf0000000)
	{
		reset_mask_max --;
		if(reset_mask_max == 0xf0000000)
			reset_mask_send = reset_mask_type;
		return;
	}
	if(point_num > 1 || pp[0][0].all == 0)
	{
		reset_mask_count = 0;
		reset_mask_max = 0;
		reset_mask_count = 0;
		return;
	}
	reset_mask_count ++;
	if(reset_mask_max == 0)
		reset_mask_max = pp[0][0].all;
	else 
		if(PointDistance((gsl_POINT_TYPE*)(&reset_mask_max),pp[0]) > (((unsigned int)reset_mask_dis) & 0xffffff)
		&& reset_mask_count > (((unsigned int)reset_mask_dis) >> 24))
			reset_mask_max = 0xfffffff1;
}

static int ConfigCoorMulti(int data[])
{
	int i,j;
	int n = 0;
	for(i=0;i<4;i++)
	{
		if(data[247+i]!=0)
		{
			if((data[247+i]&63)==0 && (data[247+i]>>16)<4)
				n++;
			else
				return FALSE;
		}
		if(data[251+i]!=0)
		{
			if((data[251+i]&63)==0 && (data[251+i]>>16)<4)
				n++;
			else
				return FALSE;
		}
	}
	if(n == 0 || n > 4)
		return FALSE;
	for(j=0;j<n;j++)
	{
		for(i=0;i<64;i++)
		{
			if(data[256+j*64+i] >= 64)
				return FALSE;
			if(i)
			{
				if(data[256+j*64+i] < data[256+j*64+i-1])
					return FALSE;
			}
		}
	}
	return TRUE;
}

static int ConfigFilter(unsigned int data[])
{
	int i;
	unsigned int ps_c[8];
	unsigned int pr_c[8];
	unsigned int sum = 0;
	if(data[242]>1 && (data[255]>=0 && data[255]<=256))
	{
		for(i=0;i<8;i++)
		{
			ps_c[i] = (data[243+i/4] >> ((i%4)*8)) & 0xff;
			pr_c[i] = (data[243+i/4+2] >> ((i%4)*8)) & 0xff;
			if(ps_c[i] >= 0x80)
				ps_c[i] |= 0xffffff00;
			if(pr_c[i] >= 0x80)
				pr_c[i] |= 0xffffff00;
			sum += ps_c[i];
			sum += pr_c[i];
		}
		if(sum == data[242] || sum + data[242] == 0)
			return TRUE;
	}
	return FALSE;
}

static int ConfigKeyMap(int data[])
{
	int i;
	if(data[217] != 1)
		return FALSE;
	for(i=0;i<8;i++)
	{
		if(data[218+2] == 0)
			return FALSE;
		if((data[218+i*3+0]>>16) > (data[218+i*3+0]&0xffff))
			return FALSE;
		if((data[218+i*3+1]>>16) > (data[218+i*3+1]&0xffff))
			return FALSE;
	}
	return TRUE;
}

static int DiagonalDistance(gsl_POINT_TYPE *p,int type)
{
	int divisor,square;
	divisor = ((int)sen_num_nokey * (int)sen_num_nokey + (int)drv_num_nokey * (int)drv_num_nokey)/16;
	if(divisor == 0)
		divisor = 1;
	if(type == 0)
		square = ((int)sen_num_nokey*(int)(p->x) - (int)drv_num_nokey*(int)(p->y)) / 4;
	else
		square = ((int)sen_num_nokey*(int)(p->x) + (int)drv_num_nokey*(int)(p->y) - (int)sen_num_nokey*(int)drv_num_nokey*64) / 4;
	return square * square / divisor;
}

static void DiagonalCompress(gsl_POINT_TYPE *p,int type,int dis,int dis_max)
{
	int x,y;
	int tx,ty;
	int cp_ceof;
	if(dis > dis_max)
		cp_ceof = (dis - dis_max)*128/(3*dis_max) + 128;
	else
		cp_ceof = 128;
	// 	else
	// 		cp_ceof = (dis_max - dis)*128/dis_max + 128;
	if(cp_ceof > 256)
		cp_ceof = 256;
	x = p->x;
	y = p->y;
	if(type)
		y = (int)sen_num_nokey*64 - y;
	x *= (int)sen_num_nokey;
	y *= (int)drv_num_nokey;
	tx = x;
	ty = y;
	x = ((tx+ty)+(tx-ty)*cp_ceof/256)/2;
	y = ((tx+ty)+(ty-tx)*cp_ceof/256)/2;
	x /= (int)sen_num_nokey;
	y /= (int)drv_num_nokey;
	if(type)
		y = sen_num_nokey*64 - y;
	if(x < 1)
		x = 1;
	if(y < 1)
		y = 1;
	if(x >= (int)drv_num_nokey*64)
		x = drv_num_nokey*64 - 1;
	if(y >= (int)sen_num_nokey*64)
		y = (int)sen_num_nokey*64 - 1;
	p->x = x;
	p->y = y;
}

static void PointDiagonal(void)
{
	int i;
	int diagonal_size;
	int dis;
	unsigned int diagonal_start;
	if(diagonal == 0)
		return;
	diagonal_size = diagonal * diagonal;
	diagonal_start = diagonal * 3/2;
	for(i=0;i<POINT_MAX;i++)
	{
		if(ps[0][i].all == 0 || ps[0][i].key != 0)// || ps[0][i].fill!=0)
		{
			point_corner &= ~(0x3<<i*2);
			continue;
		}
		else if((point_corner & (0x3<<i*2)) == 0)
		{
			if((ps[0][i].x <= diagonal_start && ps[0][i].y <= diagonal_start)
			|| (ps[0][i].x >= drv_num_nokey*64 - diagonal_start && ps[0][i].y >= sen_num_nokey*64 - diagonal_start))
				point_corner |= 0x2<<i*2;
			else if((ps[0][i].x <= diagonal_start && ps[0][i].y >= sen_num_nokey*64 - diagonal_start)
			|| (ps[0][i].x >= drv_num_nokey*64 - diagonal_start && ps[0][i].y <= diagonal_start))
				point_corner |= 0x3<<i*2;
			else
				point_corner |= 0x1<<i*2;
		}
		if(point_corner & (0x2<<i*2))
		{
			dis = DiagonalDistance(&(ps[0][i]),point_corner & (0x1<<i*2));
			if(dis <= diagonal_size*4)
			{
				DiagonalCompress(&(ps[0][i]),point_corner & (0x1<<i*2),dis,diagonal_size);
			}
			else if(dis > diagonal_size*4)
			{
				point_corner &= ~(0x3<<i*2);
				point_corner |= 0x1<<i*2;
			}
		}
	}
}

static void TouchNear(void)
{
	int i;
	int data_now;
	int ac;
	int near_buf[8];
	if(global_flag.c2f_able == 0)
		return;
	data_now = ((unsigned int)point_num) >> 16;
	if(data_now == 0)
	{
		near_out = 16;
		near_count = 0;
		near_period = 1000;
		near_data_prev = 0;
		return;
	}
	ac = point_num & 0x800 ? 1 : 0;
 	if(near_count >= 0x70000000)
 		near_count = 0x10000000;
	if(data_now != near_data_prev)
	{
		if(inte_count - near_inte < near_period)
			near_period = inte_count - near_inte;
		near_inte = inte_count;
		near_data_prev = data_now;
		near_ignore = 0;
		near_data[near_count++ & 127] = data_now;
	}
	else
	{
		if(near_ignore++ < near_period*3/2)
			return;
		near_ignore = 0;
		near_data[near_count++ & 127] = data_now;
	}
	if(ac == 0 && near_count >= 8 && near_count < 32+8)
	{
		near_refe_start=0;
		for(i=4;i<near_count - near_count/4;i++)
			near_refe_start += near_data[i];
		near_refe_start /= near_count - 4 - near_count/4;
	}
	if(ac != 0 && near_count >= 16 && near_count < 32+8)
	{
		near_refe_start=0;
		for(i=4;i<near_count - 8;i++)
			near_refe_start += near_data[i];
		near_refe_start /= near_count - 4 - 8;
	}
	for(i=0;i<8;i++)
		near_buf[i] = near_data[(near_count - i) & 127];
	SortBubble(near_buf,8);
	data_now = 0;
	for(i=2;i<6;i++)
		data_now += near_buf[i];
	data_now /= 4;
	data_now = near_refe_start - data_now;
	if(data_now > near_set[0] && near_out>=0)
	{
		near_out -= 2;
		if(near_out<0)
			near_out = -16;
	}
	if(data_now < near_set[1] && near_out<0)
	{
		near_out += 1;
		if(near_out >= 0)
			near_out = 16;
	}
}

static void PressureSave(void)
{
	int i;
	if((point_num & 0x1000)==0)
	{
		for(i=0;i<POINT_MAX;i++)
		{
			pressure_now[i] = 0;
			pressure_report[i] = 0;
		}
		return;
	}
	for(i=0;i<POINT_MAX;i++)
	{
		pressure_now[i] = point_now[i].id;
		point_now[i].id = 0;
	}
}

static void PointPressure(void)
{
	int i,j;
	for(i=0;i<POINT_MAX;i++)
	{
		if(pa[0][i]!=0 && pa[1][i]==0)
		{
			pressure_report[i] = pa[0][i]*5;
			for(j=1;j<PRESSURE_DEEP;j++)
				pa[j][i] = pa[0][i];
		}
		j = (pressure_report[i]+1)/2 + pa[0][i] + pa[1][i] + (pa[2][i]+1)/2 - pressure_report[i];
		if(j >= 2)
			j -= 2;
		else if(j <= -2)
			j += 2;
		else
			j = 0;
		pressure_report[i] = pressure_report[i]+j;
	}
}

void gsl_ReportPressure(unsigned int *p)
{
	int i;
	for(i=0;i<POINT_MAX;i++)
	{
		if(i < point_num)
		{
			if(pressure_now[i] == 0)
				p[i] = 0;
			else if(pressure_now[i] <= 7)
				p[i] = 1;
			else if(pressure_now[i] > 63+7)
				p[i] = 63;
			else
				p[i] = pressure_now[i] - 7;
		}
		else
			p[i] = 0;
	}
}
EXPORT_SYMBOL(gsl_ReportPressure);

int gsl_TouchNear(void)
{
	if(near_out < 0)
		return 1;
	else
		return 0;
}
EXPORT_SYMBOL(gsl_TouchNear);

static void gsl_id_reg_init(int flag)
{
	int i,j;
	
	for(j=0;j<POINT_DEEP;j++)
		for(i=0;i<POINT_MAX;i++)
			point_array[j][i].all = 0;	
	for(j=0;j<PRESSURE_DEEP;j++)
		for(i=0;i<POINT_MAX;i++)
			pressure_array[j][i] = 0;
	for(i=0;i<POINT_MAX;i++)
	{
		point_delay[i].all = 0;
		filter_deep[i] = 0;
	}
	point_n = 0;
	if(flag)
		point_num = 0;
	prev_num = 0;
	point_shake = 0;
	reset_mask_send = 0;
	reset_mask_max = 0;
	reset_mask_count = 0;
	point_near = 0;
	point_corner = 0;
	point_reset= 1;
}

static int DataCheck(void)
{
	if(drv_num==0 || drv_num_nokey==0 || sen_num==0 || sen_num_nokey==0)
		return 0;
	if(screen_x_max==0 || screen_y_max==0)
		return 0;
	return 1;
}

void gsl_DataInit(unsigned int * conf_in)
{
	int i;
	unsigned int *conf;
	int len;
	gsl_id_reg_init(1);
	conf = config_static;
	coordinate_correct_able = 0;
	for(i=0;i<32;i++)
	{
		coordinate_correct_coe_x[i] = i;
		coordinate_correct_coe_y[i] = i;
	}
	id_first_coe = 8;
	id_speed_coe = 128*128;
	id_static_coe = 64*64;
	average = 3+1;
	soft_average = 3;
	report_delay=0;
	report_ahead = 0x9249249;
	for(i=0;i<4;i++)
		median_dis[i]=0;
	shake_min = 0*0;
	for(i=0;i<2;i++)
	{
		match_y[i]=0;
		match_x[i]=0;
		ignore_y[i]=0;
		ignore_x[i]=0;
	}
	match_y[0]=4096;
	match_x[0]=4096;
	screen_y_max = 480;
	screen_x_max = 800;
	point_num_max=10;
	drv_num = 16;
	sen_num = 10;
	drv_num_nokey = 16;
	sen_num_nokey = 10;
	for(i=0;i<4;i++)
		edge_cut[i] = 0;
	for(i=0;i<32;i++)
		stretch_array[i] = 0;
	for(i=0;i<16;i++)
		shake_all_array[i] = 0;
	reset_mask_dis = 0;
	reset_mask_type=0;
	diagonal = 0;
	key_map_able = 0;
	for(i=0;i<8*3;i++)
		key_range_array[i] = 0;
	filter_able = 0;
	filter_coe[0] = ( 0<<6*4)+( 0<<6*3)+( 0<<6*2)+(40<<6*1)+(24<<6*0);
	filter_coe[1] = ( 0<<6*4)+( 0<<6*3)+(16<<6*2)+(24<<6*1)+(24<<6*0);
	filter_coe[2] = ( 0<<6*4)+(16<<6*3)+(24<<6*2)+(16<<6*1)+( 8<<6*0);
	filter_coe[3] = ( 6<<6*4)+(16<<6*3)+(24<<6*2)+(12<<6*1)+( 6<<6*0);
	for(i=0;i<4;i++)
	{
		multi_x_array[i]=0;
		multi_y_array[i]=0;
	}
	point_repeat[0] = 32;
	point_repeat[1] = 96;
	if(conf_in == NULL)
	{
		return;
	}
	if(conf_in[0] <= 0xfff)
	{
		if(ConfigCoorMulti(conf_in))
			len = 512;
		else if(ConfigFilter(conf_in))
			len = 256;
		else if(ConfigKeyMap(conf_in)) 
			len = 241;//key_map
		else
			len = 215;//reset_mask
	}
	else if(conf_in[1] <= CONFIG_LENGTH)
		len = conf_in[1];
	else
		len = CONFIG_LENGTH;
	for(i=0;i<len;i++)
		conf[i] = conf_in[i];
	for(;i<CONFIG_LENGTH;i++)
		conf[i] = 0;
	if(conf_in[0] <= 0xfff)
	{
		coordinate_correct_able = conf[0];
		drv_num = conf[1];
		sen_num = conf[2];
		drv_num_nokey = conf[3];
		sen_num_nokey = conf[4];
		id_first_coe = conf[5];
		id_speed_coe = conf[6];
		id_static_coe = conf[7];
		average = conf[8];
		soft_average = conf[9];

		report_delay = conf[13];
		shake_min = conf[14];
		screen_y_max = conf[15];
		screen_x_max = conf[16];
		point_num_max = conf[17];
		global_flag.all = conf[18];
		for(i=0;i<4;i++)
			median_dis[i] = conf[19+i];
		for(i=0;i<2;i++)
		{
			match_y[i] = conf[23+i];
			match_x[i] = conf[25+i];
			ignore_y[i] = conf[27+i];
			ignore_x[i] = conf[29+i];
		}
		for(i=0;i<64;i++)
		{
			coordinate_correct_coe_x[i] = conf[31+i];
			coordinate_correct_coe_y[i] = conf[95+i];
		}
		for(i=0;i<4;i++)
			edge_cut[i] = conf[159+i];
		for(i=0;i<32;i++)
			stretch_array[i] = conf[163+i];
		for(i=0;i<16;i++)
			shake_all_array[i] = conf[195+i];
		reset_mask_dis = conf[213];
		reset_mask_type = conf[214];
		key_map_able = conf[217];
		for(i=0;i<8*3;i++)
			key_range_array[i] = conf[218+i];
		filter_able = conf[242];
		for(i=0;i<4;i++)
			filter_coe[i] = conf[243+i];
		for(i=0;i<4;i++)
			multi_x_array[i] = conf[247+i];
		for(i=0;i<4;i++)
			multi_y_array[i] = conf[251+i];
		diagonal = conf[255];
		for(i=0;i<256;i++)
			multi_group[0][i] = conf[256+i];
		for(i=0;i<32;i++)
		{
			ps_coe[0][i] = conf[256 + 64*3 + i];
			pr_coe[0][i] = conf[256 + 64*3 + i + 32];
		}
		//-----------------------
		near_set[0] = 0;
		near_set[1] = 0;
	}
	else
	{
		global_flag.all = conf[0x10];
		point_num_max = conf[0x11];
		drv_num = conf[0x12]&0xffff;
		sen_num = conf[0x12]>>16;
		drv_num_nokey = conf[0x13]&0xffff;
		sen_num_nokey = conf[0x13]>>16;
		screen_x_max = conf[0x14]&0xffff;
		screen_y_max = conf[0x14]>>16;
		average = conf[0x15];
		reset_mask_dis = conf[0x16];
		reset_mask_type = conf[0x17];
		point_repeat[0] = conf[0x18]>>16;
		point_repeat[1] = conf[0x18]&0xffff;
		//conf[0x19~0x1f]
		near_set[0] = conf[0x19]>>16;
		near_set[1] = conf[0x19]&0xffff;
		diagonal = conf[0x1a];
		//-------------------------
		
		id_first_coe = conf[0x20];
		id_speed_coe = conf[0x21];
		id_static_coe = conf[0x22];
		match_y[0] = conf[0x23]>>16;
		match_y[1] = conf[0x23]&0xffff;
		match_x[0] = conf[0x24]>>16;
		match_x[1] = conf[0x24]&0xffff;
		ignore_y[0] = conf[0x25]>>16;
		ignore_y[1] = conf[0x25]&0xffff;
		ignore_x[0] = conf[0x26]>>16;
		ignore_x[1] = conf[0x26]&0xffff;
		edge_cut[0] = (conf[0x27]>>24) & 0xff;
		edge_cut[1] = (conf[0x27]>>16) & 0xff;
		edge_cut[2] = (conf[0x27]>> 8) & 0xff;
		edge_cut[3] = (conf[0x27]>> 0) & 0xff;
		report_delay = conf[0x28];
		shake_min = conf[0x29];
		for(i=0;i<16;i++)
		{
			stretch_array[i*2+0] = conf[0x2a+i] & 0xffff;
			stretch_array[i*2+1] = conf[0x2a+i] >> 16;
		}
		for(i=0;i<8;i++)
		{
			shake_all_array[i*2+0] = conf[0x3a+i] & 0xffff;
			shake_all_array[i*2+1] = conf[0x3a+i] >> 16;
		}
		report_ahead = conf[0x42];
		
		key_map_able = conf[0x60];
		for(i=0;i<8*3;i++)
			key_range_array[i] = conf[0x61+i];
		
		coordinate_correct_able = conf[0x100];
		for(i=0;i<4;i++)
		{
			multi_x_array[i] = conf[0x101+i];	
			multi_y_array[i] = conf[0x105+i];
		}
		for(i=0;i<64;i++)
		{
			coordinate_correct_coe_x[i] = (conf[0x109+i/4]>>(i%4*8)) & 0xff;
			coordinate_correct_coe_y[i] = (conf[0x109+64/4+i/4]>>(i%4*8)) & 0xff;
		}	
		for(i=0;i<256;i++)
		{
			multi_group[0][i] = (conf[0x109+64/4*2+i/4]>>(i%4*8)) & 0xff;
		}

		filter_able = conf[0x180];
		for(i=0;i<4;i++)
			filter_coe[i] = conf[0x181+i];
		for(i=0;i<4;i++)
			median_dis[i] = conf[0x185+i];
		for(i=0;i<32;i++)
		{
			ps_coe[0][i] = conf[0x189 + i];
			pr_coe[0][i] = conf[0x189 + i + 32];
		}
	}
	//---------------------------------------------
	for(i=0;i<2;i++)
	{
		if(match_x[i] & 0x8000)
			match_x[i] |= 0xffff0000;
		if(match_y[i] & 0x8000)
			match_y[i] |= 0xffff0000;
		if(ignore_x[i] & 0x8000)
			ignore_x[i] |= 0xffff0000;
		if(ignore_y[i] & 0x8000)
			ignore_y[i] |= 0xffff0000;
	}
}
EXPORT_SYMBOL(gsl_DataInit);

unsigned int gsl_version_id(void)
{
	return GSL_VERSION;
}
EXPORT_SYMBOL(gsl_version_id);

unsigned int gsl_mask_tiaoping(void)
{
	return reset_mask_send;
}
EXPORT_SYMBOL(gsl_mask_tiaoping);



void gsl_alg_id_main(struct gsl_touch_info *cinfo)
{
	int i = 0;
	int num_save;
	point_num = cinfo->finger_num;
	if(((point_num & 0x100)!=0)
	|| ((point_num & 0x200)!=0 && point_reset == 0))
	{
		gsl_id_reg_init(0);//point_reset = 1;
	}
	if((point_num & 0x300) == 0)
	{
		point_reset = 0;
	}
	if(point_num & 0x400)
		point_only = 1;	
	else
		point_only = 0;
	inte_count ++;	
	num_save = point_num & 0xff;
	if(num_save > POINT_MAX)
		num_save = POINT_MAX;
	for(i=0;i<POINT_MAX;i++)
	{
		if(i >= num_save)
			point_now[i].all = 0;
		else
			point_now[i].all = (cinfo->id[i]<<28) | (cinfo->x[i]<<16) | cinfo->y[i];
	}
	if(DataCheck() == 0)
	{
		point_num = 0;
		cinfo->finger_num = 0;	
		return;
	}
	TouchNear();
	PressureSave();
	point_num = num_save;
	CoordinateCorrect();
	PointEdge();
	PointRepeat();
	GetPointNum(point_now);
	PointPointer();
	PointPredict();
	PointId();
	PointNewId();
	PointOrder();
	PointCross();
	GetPointNum(pp[0]);
	prev_num = point_num;
	ResetMask();
	PointStretch();
	PointDiagonal();
 	PointFilter();
	PointDelay();
	PointPressure();
	PointReport(cinfo);
}
EXPORT_SYMBOL(gsl_alg_id_main);


