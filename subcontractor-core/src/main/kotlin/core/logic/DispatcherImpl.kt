package core.logic

open class DispatcherImpl<WorkResults>(
    private val database: DataStorage<WorkResults>
) : Dispatcher<WorkResults> {

    override fun registerWork(
        workId: WorkId,
        customerId: UserId,
        executorsId: List<UserId>
    ): Map<UserId, OrderId> {
        var customer = Customer(customerId, mapOf())
        database.addWork(workId, customer)
        val orders = mutableMapOf<UserId, OrderId>()
        for (id in executorsId) {
            val orderId = OrderId(id, id)
            val order = Order(orderId, OrdersChain(listOf(), listOf()), DispatcherUtil.getCurrentTime())
            orders[id] = orderId
            database.addOrder(workId, orderId, order)
            val worker = Worker(id)
            worker.addOrder(orderId)
            database.addWorker(workId, worker)
        }
        customer = Customer(customerId, orders)
        database.modifyCustomer(workId, customer)
        return orders
    }

    override fun deleteWork(workId: WorkId) {
        database.deleteWork(workId)
    }

    private fun isContainsCycle(ordersChain: OrdersChain, dstId: UserId): Boolean {
        return ordersChain.getExecutorsId().contains(dstId)
    }

    private fun delegateOrder(
        workId: WorkId,
        orderId: OrderId,
        customer: Worker,
        dstId: UserId
    ): OrderId {
        val time = DispatcherUtil.getCurrentTime()
        val order = database.getOrder(workId, orderId)

        val ordersChain = OrdersChain(order.ordersChain.orders + orderId, order.ordersChain.times + time)

        if (isContainsCycle(ordersChain, dstId)) {
            throw DispatcherError.CycleFound
        }

        var worker: Worker
        try {
            worker = database.getWorker(workId, dstId)
        } catch (error: DispatcherError.WorkerNotFound) {
            worker = Worker(dstId)
            database.addWorker(workId, worker)
        }

        val newOrderId = OrderId(orderId.customerId, dstId)
        val newOrder = Order(newOrderId, ordersChain, time)
        database.addOrder(workId, newOrderId, newOrder)
        worker.addOrder(newOrderId)
        customer.addDelegation(orderId, dstId, time)
        database.modifyWorker(workId, worker)
        return newOrderId
    }


    override fun delegateOrder(
        workId: WorkId,
        orderId: OrderId,
        srcId: UserId,
        dstId: UserId
    ): OrderId {
        val customer = database.getWorker(workId, srcId)
        val newOrderId = delegateOrder(workId, orderId, customer, dstId)
        database.modifyWorker(workId, customer)
        return newOrderId
    }


    private fun executeOrder(
        workId: WorkId,
        orderId: OrderId,
        customer: Customer,
        results: WorkResults?,
        prevChain: OrdersChain = OrdersChain(listOf(), listOf())
    ) {

        val order = database.getOrder(workId, orderId)

        val chain = order.ordersChain
        val indices = order.ordersChain.orders.indices

        for (i in indices) {
            val worker = database.getWorker(workId, chain.orders[i].executorId)

            if (!worker.containsDelegation(chain.orders[i], chain.times[i])) {
                throw DispatcherError.DelegationIsNotRelevant
            }
        }

        database.addWorkResult(workId, orderId, results)
        customer.setWorkReport(orderId.customerId, WorkReport((prevChain.orders + order.ordersChain.orders + orderId).reversed(), orderId))
    }


    override fun executeOrder(workId: WorkId, orderId: OrderId, results: WorkResults?) {
        val customer = database.getCustomer(workId)
        executeOrder(workId, orderId, customer, results)
        database.modifyCustomer(workId, customer)
    }


    override fun cancelExecution(workId: WorkId, orderId: OrderId) {
        executeOrder(workId, orderId, null)
    }

    override fun cancelDelegation(workId: WorkId, orderId: OrderId) {
        val worker = database.getWorker(workId, orderId.executorId)
        worker.deleteDelegation(orderId)
        database.modifyWorker(workId, worker)
        cancelExecution(workId, orderId)
    }

    override fun getWorkResults(workId: WorkId): Map<UserId, WorkResults?> {
        val customer = database.getCustomer(workId)
        val reports = customer.getWorkResults()
        val results = mutableMapOf<UserId, WorkResults?>()
        for (report in reports) {
            if (report.value != null) {
                results[report.key] = database.getWorkResult(workId, report.value!!.resultId)
            } else {
                results[report.key] = null
            }
        }
        return results
    }

    override fun getTheMostActiveRealExecutors(workId: WorkId, count: Int): Map<UserId, Int> {
        val customer = database.getCustomer(workId)
        val realExecutors = customer.getRealExecutors()
        val worksCount = mutableMapOf<UserId, Int>()
        for (userId in realExecutors.values) {
            if (worksCount[userId] == null) {
                worksCount[userId] = 1
            } else {
                worksCount[userId] = worksCount[userId]!! + 1
            }
        }
        val counts = worksCount.values.toList().sorted().reversed()
        if (counts.isEmpty()) {
            return worksCount
        }
        val pivot = counts.subList(0, count).last()
        return worksCount.filter { it -> it.value >= pivot }
    }

    override fun getCustomerId(workId: WorkId): UserId {
        return database.getCustomer(workId).userId
    }

    override fun getExecutorsId(workId: WorkId): List<UserId> {
        val customer = database.getCustomer(workId)
        return customer.getExecutorsId()
    }

    override fun delegateAllOrders(workId: WorkId, srcId: UserId, dstId: UserId): List<OrderId> {
        var customer = database.getWorker(workId, srcId)
        val ordersId = customer.getOrders()
        var ordersList = mutableListOf<OrderId>()
        val resultList = mutableListOf<OrderId>()
        for (orderId in ordersId) {
            ordersList.add(delegateOrder(workId, orderId, customer, dstId))
        }
        database.modifyWorker(workId, customer)
        resultList.addAll(ordersList)

        var delegations = getDelegations(workId, dstId)
        var customerId = dstId
        while (delegations.isNotEmpty()) {
            val toUserId = delegations.values.first()
            customer = database.getWorker(workId, customerId)
            val list = mutableListOf<OrderId>()
            for (orderId in ordersList) {
                list.add(delegateOrder(workId, orderId, customer, toUserId))
            }
            resultList.addAll(list)
            ordersList = list
            delegations = getDelegations(workId, toUserId)
            customerId = toUserId
            database.modifyWorker(workId, customer)
        }
        return resultList
    }

    override fun executeAllOrders(workId: WorkId, userId: UserId, results: WorkResults?) {
        val worker = database.getWorker(workId, userId)
        val customer = database.getCustomer(workId)
        val ordersId = worker.getOrders()
        for (orderId in ordersId) {
            executeOrder(workId, orderId, customer, results)
        }
        database.modifyCustomer(workId, customer)

    }

    override fun cancelAllExecutions(workId: WorkId, userId: UserId) {
        executeAllOrders(workId, userId, null)
    }

    override fun cancelAllDelegations(workId: WorkId, userId: UserId) {
        val worker = database.getWorker(workId, userId)
        for (orderId in worker.getOrders()) {
            worker.deleteDelegation(orderId)
        }
        database.modifyWorker(workId, worker)
        cancelAllExecutions(workId, userId)
    }

    override fun getDelegations(workId: WorkId, userId: UserId): Map<OrderId, UserId> {
        val worker = database.getWorker(workId, userId)
        val delegations = worker.getDelegations()
        return delegations.map { it -> OrderId(it.key, it.value.first) to it.value.second.first }.toMap()
    }

    override fun getOrdersId(workId: WorkId, userId: UserId): List<OrderId> {
        val worker = database.getWorker(workId, userId)
        return worker.getOrders()
    }

    override fun getWorkResults(workId: WorkId, orderId: OrderId): WorkResults? {
        val customer = database.getCustomer(workId)
        val result = customer.getWorkResults(orderId.executorId)?.get(orderId)
        return if (result == null) null else database.getWorkResult(workId, result.resultId)
    }
}