package com.yupao.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupao.model.domain.Tag;
import com.yupao.service.TagService;
import com.yupao.mapper.TagMapper;
import org.springframework.stereotype.Service;

/**
* @author 16013
* @description 针对表【tag(标签)】的数据库操作Service实现
* @createDate 2025-02-06 21:40:25
*/
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag>
    implements TagService{

}




