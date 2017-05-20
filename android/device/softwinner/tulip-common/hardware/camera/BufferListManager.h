
#ifndef __HAL_BUFFER_LIST_H__
#define __HAL_BUFFER_LIST_H__

#include <fcntl.h> 
#include <cutils/list.h>
#include <utils/Mutex.h>

namespace android {

typedef struct BUFFER_NODE_t
{
	struct listnode	i_list; 
	int				id;
	void *			data;
	int				size;
	char			priv[128];
	int             fd;
}buffer_node;

class BufferListManager {
public:
	BufferListManager();
	~BufferListManager();

	// return buffer start address
	buffer_node * allocBuffer(uint32_t id, uint32_t min_size);
	void releaseBuffer(buffer_node * node);
	
	bool isListEmpty();

	buffer_node * pop();
	void push(buffer_node * node);
	int getItemCnt();
	
private:
	Mutex				mLock;

	struct listnode		mList;

	int					mItemCnt;
};

}; /* namespace android */

#endif  /* __HAL_BUFFER_LIST_H__ */

