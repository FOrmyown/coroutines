package com.as.suspension.user;


import java.io.Serializable;

/**
 * @author 11192
 */
public class ArgumentFrame implements Serializable{
    private static final long serialVersionUID = 84581514L;

    private Object[] args;

    public ArgumentFrame(Object... args){
        this.args = args;
    }

    public Object[] getArgs() {
        return args;
    }

    public boolean hasArgs(){
        return args.length != 0;
    }
}
