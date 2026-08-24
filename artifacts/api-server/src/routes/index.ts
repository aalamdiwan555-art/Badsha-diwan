import { Router, type IRouter } from "express";
import healthRouter from "./health";
import accountRouter from "./account";
import adminRouter from "./admin";
import authRouter from "./auth";

const router: IRouter = Router();

router.use(healthRouter);
router.use(authRouter);
router.use(accountRouter);
router.use(adminRouter);

export default router;
