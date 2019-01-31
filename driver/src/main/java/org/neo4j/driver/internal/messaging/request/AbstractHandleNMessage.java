/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.driver.internal.messaging.request;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.neo4j.driver.internal.messaging.Message;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;

import static org.neo4j.driver.internal.util.MetadataExtractor.ABSENT_STATEMENT_ID;

public abstract class AbstractHandleNMessage implements Message
{
    private final Map<String,Value> metadata = new HashMap<>();

    AbstractHandleNMessage( long n, long id )
    {
        this.metadata.put( "n", Values.value( n ) );
        if ( id != ABSENT_STATEMENT_ID )
        {
            this.metadata.put( "stmt_id", Values.value( id ) );
        }
    }

    public Map<String,Value> metadata()
    {
        return metadata;
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }
        AbstractHandleNMessage that = (AbstractHandleNMessage) o;
        return Objects.equals( metadata, that.metadata );
    }

    protected abstract String name();

    @Override
    public int hashCode()
    {
        return Objects.hash( metadata );
    }

    @Override
    public String toString()
    {
        return String.format( "%s %s", name(), metadata );
    }

}
